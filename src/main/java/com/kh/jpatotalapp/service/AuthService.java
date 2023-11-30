package com.kh.jpatotalapp.service;

import com.kh.jpatotalapp.dto.AccessTokenDto;
import com.kh.jpatotalapp.dto.MemberReqDto;
import com.kh.jpatotalapp.dto.MemberResDto;
import com.kh.jpatotalapp.dto.TokenDto;
import com.kh.jpatotalapp.entity.Member;
import com.kh.jpatotalapp.entity.RefreshToken;
import com.kh.jpatotalapp.jwt.TokenProvider;
import com.kh.jpatotalapp.repository.MemberRepository;
import com.kh.jpatotalapp.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final AuthenticationManagerBuilder managerBuilder;// 인증을 담당하는 클래스
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public MemberResDto signup(MemberReqDto requestDto) {
        if(memberRepository.existsByEmail(requestDto.getEmail())){
            throw new RuntimeException("이미 가입되어 있는 유저입니다.");
        }
        Member member = requestDto.toEntity(passwordEncoder);
        return MemberResDto.of(memberRepository.save(member));
    }

    public TokenDto login(MemberReqDto requestDto) {
        UsernamePasswordAuthenticationToken authenticationToken = requestDto.toAuthentication();
        log.info("authenticationToken : {}", authenticationToken);

        Authentication authentication = managerBuilder.getObject().authenticate(authenticationToken);
        log.info("authentication : {}", authentication);

        TokenDto token = tokenProvider.generateTokenDto(authentication);

        // refreshToKen DB에 저장
        Member member = memberRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(()->new RuntimeException("존재하지 않는 이메일입니다."));

        // 이미 db에 해당 계정으로 저장된 refreshToken 정보가 있다면 삭제
        log.info("Exists by member: {}", refreshTokenRepository.existsByMember(member));
        if(refreshTokenRepository.existsByMember(member)) {
            refreshTokenRepository.deleteByMember(member);
        }

        RefreshToken refreshToken = new RefreshToken();
        String encodedToken = token.getRefreshToken();
        refreshToken.setRefreshToken(encodedToken.concat("="));
        refreshToken.setRefreshTokenExpiresIn(token.getRefreshTokenExpiresIn());
        refreshToken.setMember(member);

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    public AccessTokenDto refreshAccessToken(String refreshToken) {
        log.info("refreshToken : {}", refreshToken);
        log.info("refreshExist : {}", refreshTokenRepository.existsByRefreshToken(refreshToken));

        // DB에 일치하는 refreshToken이 있으면
        if(refreshTokenRepository.existsByRefreshToken(refreshToken)) {
            // refreshToken 검증
            try {
                if (tokenProvider.validateToken(refreshToken)) {
                    return tokenProvider.generateAccessTokenDto(tokenProvider.getAuthentication(refreshToken));
                }
            }catch (RuntimeException e) {
                log.error("토큰 유효성 검증 중 예외 발생: {}", e.getMessage());
            }
        }

        return null;
    }
}
