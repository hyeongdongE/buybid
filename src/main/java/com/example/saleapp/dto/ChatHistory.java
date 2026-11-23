package com.example.saleapp.dto;

import java.util.List;

public record ChatHistory(List<ChatDTO> messages, Long nextBefore) {}
