package com.kh.jpatotalapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.jpatotalapp.dto.ChatMessageDto;
import com.kh.jpatotalapp.dto.ChatRoomResDto;
import com.kh.jpatotalapp.entity.Chat;
import com.kh.jpatotalapp.entity.ChatRoom;
import com.kh.jpatotalapp.repository.ChatRepository;
import com.kh.jpatotalapp.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChatService {
    private final ObjectMapper objectMapper;
    private final ChatRepository chatRepository;
    private final ChatRoomRepository chatRoomRepository;
    private Map<String, ChatRoomResDto> chatRooms;

    @PostConstruct // 의존성 주입 이후 초기화를 수행하는 메소드
    private void init() {
        chatRooms = new LinkedHashMap<>();
    }
    public List<ChatRoomResDto> findAllRoom() {
        return new ArrayList<>(chatRooms.values());
    }
    public ChatRoomResDto findRoomById(String roomId) {
        return chatRooms.get(roomId);
    }

    // 방 개설하기
    public ChatRoomResDto createRoom(String name) {
        String randomId = UUID.randomUUID().toString();
        log.info("UUID : " + randomId);
        ChatRoomResDto chatRoom = ChatRoomResDto.builder()
                .roomId(randomId)
                .name(name)
                .regDate(LocalDateTime.now())
                .build();
        chatRooms.put(randomId, chatRoom);

        // DB에 새로운 채팅방 저장
        ChatRoom newChatRoom = new ChatRoom();
        newChatRoom.setRoomId(chatRoom.getRoomId());
        newChatRoom.setRoomName(chatRoom.getName());
        newChatRoom.setCreatedAt(chatRoom.getRegDate());
        chatRoomRepository.save(newChatRoom);

        return chatRoom;
    }
    public void removeRoom(String roomId) {
        ChatRoomResDto room = chatRooms.get(roomId);
        if (room != null) {
            if (room.isSessionEmpty()) {
                chatRooms.remove(roomId);
                // DB에서 채팅방 삭제
                chatRoomRepository.deleteById(roomId);
            }
        }
    }

    public void addSessionAndHandleEnter(String roomId, WebSocketSession session, ChatMessageDto chatMessage) {
        ChatRoomResDto room = findRoomById(roomId);
        if (room != null) {
            room.getSessions().add(session);
            if (chatMessage.getSender() != null) {
                chatMessage.setMessage(chatMessage.getSenderName() + "님이 입장했습니다.");
                sendMessageToAll(roomId, chatMessage);

            }
            log.debug("New session added: " + session);
        }
    }

    public void removeSessionAndHandleExit(String roomId, WebSocketSession session, ChatMessageDto chatMessage) {
        ChatRoomResDto room = findRoomById(roomId);
        if (room != null) {
            room.getSessions().remove(session);
            if (chatMessage.getSender() != null) {
                chatMessage.setMessage(chatMessage.getSenderName() + "님이 퇴장했습니다.");
                sendMessageToAll(roomId, chatMessage);
            }
            log.debug("Session removed: " + session);
            if (room.isSessionEmpty()) {
                removeRoom(roomId);
            }
        }
    }

    public void sendMessageToAll(String roomId, ChatMessageDto message) {
        ChatRoomResDto room = findRoomById(roomId);
        if (room != null) {
            for (WebSocketSession session : room.getSessions()) {
                sendMessage(session, message);
            }
            saveMessage(roomId, message);
        }

    }

    public <T> void sendMessage(WebSocketSession session, T message) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch(IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    // 채팅 메세지 DB저장
    public void saveMessage(String roomId, ChatMessageDto chatMessageDto) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("해당 채팅방이 존재하지 않습니다."));
        Chat chatMessage = new Chat();
        chatMessage.setChatRoom(chatRoom);
        chatMessage.setSender(chatMessageDto.getSender());
        chatMessage.setMessage(chatMessageDto.getMessage());
        chatMessage.setSentAt(LocalDateTime.now());
        chatMessage.setSenderName(chatMessageDto.getSenderName());
        chatRepository.save(chatMessage);
    }
    // 이전 채팅 가져오기
    public List<Chat> getRecentMessages(String roomId) {
        return chatRepository.findRecentMessages(roomId);
    }
}
