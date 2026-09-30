package com.leadfold.service;

// One text message extracted from a Meta WhatsApp webhook payload.
public record IncomingMessage(String phoneNumberId, String from, String text) {}
