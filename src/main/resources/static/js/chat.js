import { authenticatedFetch, refreshSession } from "/js/auth.js";

document.addEventListener('DOMContentLoaded', () => {
    const chatBox = document.getElementById('chat-box');
    const roomId = Number(chatBox.dataset.roomId);
    const userId = Number(chatBox.dataset.userId);
    const input = document.getElementById('chat-input');
    const sendBtn = document.getElementById('send-btn');

    const previous = document.getElementById('previous-chat');
    const messages = new Map();
    let before;
    let loading = false;
    let stomp;
    let _lastMinuteKey = null;
    let _lastDateKey = null;
    let _lastSide = null;

    async function loadHistory(cursor) {
        if (loading) return;
        loading = true;
        previous.disabled = true;
        try {
            const query = cursor ? `?before=${encodeURIComponent(cursor)}` : '';
            const response = await authenticatedFetch(`/chat/history/${roomId}${query}`);
            if (!response.ok) throw new Error("Unable to load chat history");
            const page = await response.json();
            const oldHeight = chatBox.scrollHeight;
            const oldTop = chatBox.scrollTop;
            before = page.nextBefore;
            for (const message of page.messages) messages.set(message.chatId, message);
            chatBox.replaceChildren();
            _lastMinuteKey = _lastDateKey = _lastSide = null;
            const ordered = [...messages.values()].sort((first, second) => first.chatId - second.chatId);
            for (const message of ordered) appendMessage(message, userId);
            if (cursor) chatBox.scrollTop = oldTop + chatBox.scrollHeight - oldHeight;
        } catch (error) {
            console.error("채팅 이력을 불러오지 못했습니다.", error);
        } finally {
            loading = false;
            previous.hidden = !before;
            previous.disabled = !before;
        }
    }

    previous.addEventListener('click', () => loadHistory(before));

    async function connectWS(retry = 0) {
        if (!window.SockJS || !window.Stomp) {
            console.error('SockJS/Stomp 전역 미로딩');
            return;
        }

        const sock = new SockJS('/ws');
        stomp = Stomp.over(sock);
        stomp.debug = null;
        stomp.heartbeat.outgoing = 10000;
        stomp.heartbeat.incoming = 10000;

        stomp.connect(
            {},
            () => {
                console.log('STOMP connected');
                stomp.subscribe(`/topic/room.${roomId}`, (res) => {
                    const msg = JSON.parse(res.body);
                    if (!messages.has(msg.chatId)) {
                        messages.set(msg.chatId, msg);
                        appendMessage(msg, userId);
                    }
                });
            },
            async (err) => {
                console.error('STOMP connect error:', err);
                if (retry < 1 && await refreshSession()) {
                    return connectWS(retry + 1);
                }
            }
        );
    }

    function sendMessage() {
        const content = input.value.trim();
        if (!content || !stomp || !stomp.connected) return;
        // 서버 전송
        stomp.send(`/app/chat.send.${roomId}`, {}, JSON.stringify({ content }));
        input.value = '';
    }

    function appendMessage(msg, userId) {
        const isMine = Number(msg.senderId) === Number(userId);
        const side = isMine ? 'me' : 'you';

        const t = new Date(msg.createdAt ?? Date.now());
        const yyyy = t.getFullYear();
        const mm = (t.getMonth() + 1).toString().padStart(2, '0');
        const dd = t.getDate().toString().padStart(2, '0');
        const dateKey = `${yyyy}-${mm}-${dd}`; // 날짜 비교용 키

        const hh = t.getHours();
        const mi = t.getMinutes().toString().padStart(2, '0');
        const ampm = hh >= 12 ? '오후' : '오전';

        const minuteKey = `${dateKey} ${hh}:${mi}`;
        const showTime = !(_lastMinuteKey === minuteKey && _lastSide === side);

        // ✅ 날짜가 바뀌면 구분선 추가
        if (_lastDateKey !== dateKey) {
            const divider = document.createElement('div');
            divider.className = 'text-center small text-muted my-2';
            divider.innerHTML = `
      <hr class="w-25 d-inline-block me-2">
      <span>${yyyy}년 ${mm}월 ${dd}일</span>
      <hr class="w-25 d-inline-block ms-2">
    `;
            chatBox.appendChild(divider);
            _lastDateKey = dateKey;
        }

        // 🟢 메시지 버블 생성
        const wrapper = document.createElement('div');
        wrapper.className = isMine
            ? 'd-flex flex-column align-items-end'
            : 'd-flex flex-column';

        const bubble = document.createElement('div');
        bubble.className = isMine ? 'bubble me' : 'bubble you';
        bubble.textContent = msg.content ?? '';
        wrapper.appendChild(bubble);

        if (showTime) {
            const time = document.createElement('div');
            time.className = isMine ? 'small text-muted me-1' : 'small text-muted ms-1';
            time.textContent = `${ampm} ${(hh % 12 || 12)}:${mi}`;
            wrapper.appendChild(time);
            _lastMinuteKey = minuteKey;
            _lastSide = side;
        }

        chatBox.appendChild(wrapper);
        chatBox.scrollTop = chatBox.scrollHeight;
    }

    // 이벤트 바인딩 그대로 유지
    sendBtn.addEventListener('click', sendMessage);
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') sendMessage(); });
    document.getElementById("back-btn").addEventListener('click', () => {
        location.href = "/chatlist";
    });

    (async () => {
        await loadHistory();
        await connectWS();
    })();

});
