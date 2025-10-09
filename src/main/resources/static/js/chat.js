import { getAccessToken, refreshToken } from "/js/auth.js";

document.addEventListener('DOMContentLoaded', () => {
    const chatBox = document.getElementById('chat-box');
    const roomId = Number(chatBox.dataset.roomId);
    const userId = Number(chatBox.dataset.userId);
    const input = document.getElementById('chat-input');
    const sendBtn = document.getElementById('send-btn');

    let stomp;
    let _lastMinuteKey = null;
    let _lastDateKey = null;
    let _lastSide = null;

    async function getValidToken() {
        let token = getAccessToken();
        if (!token) {
            const ok = await refreshToken();
            if (!ok) { location.href = '/signin'; return null; }
            token = getAccessToken();
        }
        return token;
    }

    async function loadHistory(token) {
        let res = await fetch(`/chat/history/${roomId}`, {
            headers: { 'Authorization': 'Bearer ' + token },
            credentials: 'include'
        });

        if (res.status === 401) {
            const ok = await refreshToken();
            if (!ok) { location.href = '/signin'; return; }
            token = getAccessToken();
            res = await fetch(`/chat/history/${roomId}`, {
                headers: { 'Authorization': 'Bearer ' + token },
                credentials: 'include'
            });
        }

        if (!res.ok) {
            console.error('히스토리 로딩 실패', res.status);
            return;
        }

        const list = await res.json();
        list.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
        _lastMinuteKey = null;
        _lastSide = null;
        for (const m of list) appendMessage(m, userId);
        chatBox.scrollTop = chatBox.scrollHeight;
    }

    async function connectWS(token, retry = 0) {
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
            { Authorization: 'Bearer ' + token },
            () => {
                console.log('STOMP connected');
                stomp.subscribe(`/topic/room.${roomId}`, (res) => {
                    const msg = JSON.parse(res.body);
                    if (Number(msg.senderId) !== Number(userId)) appendMessage(msg, userId);
                });
            },
            async (err) => {
                console.error('STOMP connect error:', err);
                if (retry < 1) {
                    const ok = await refreshToken();
                    if (ok) {
                        const newToken = getAccessToken();
                        return connectWS(newToken, retry + 1);
                    }
                }
            }
        );
    }

    function sendMessage() {
        const content = input.value.trim();
        if (!content || !stomp || !stomp.connected) return;
        appendMessage({ senderId: userId, content, createdAt: new Date() }, userId);
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
        const token = await getValidToken();
        if (!token) return;
        await loadHistory(token);
        await connectWS(token);
    })();

});