package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.Role;
import com.example.motivediet_be.domain.User;
import com.example.motivediet_be.dto.TokenDto;
import com.example.motivediet_be.dto.UserInfoDto;
import com.example.motivediet_be.jwt.TokenProvider;
import com.example.motivediet_be.repository.UserRepository;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.Principal;

@Service
public class AuthService {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_SCOPE = "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email";

    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;

    public AuthService(@Value("${google.client-id}") String googleClientId,
                        @Value("${google.client-secret}") String googleClientSecret,
                        @Value("${google.redirect-uri}") String googleRedirectUri,
                        UserRepository userRepository,
                        TokenProvider tokenProvider) {
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = googleRedirectUri;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
    }

    public String getGoogleLoginUrl() {
        return UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", googleRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", GOOGLE_SCOPE)
                .build()
                .toUriString();
    }

    public String getGoogleAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", googleRedirectUri);
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                GOOGLE_TOKEN_URL, new HttpEntity<>(params, headers), String.class);

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            String json = responseEntity.getBody();
            Gson gson = new Gson();

            return gson.fromJson(json, TokenDto.class).getAccessToken();
        }

        throw new RuntimeException("구글 엑세스 토큰을 가져오는데 실패했습니다.");
    }

    public TokenDto loginOrSignUp(String googleAccessToken) {
        UserInfoDto userInfoDto = getUserInfo(googleAccessToken);

        if (!userInfoDto.getVerifiedEmail()) {
            throw new RuntimeException("이메일 인증이 되지 않은 유저입니다.");
        }

        User user = userRepository.findByEmail(userInfoDto.getEmail()).orElseGet(() ->
                userRepository.save(User.builder()
                        .email(userInfoDto.getEmail())
                        .name(userInfoDto.getName())
                        .pictureUrl(userInfoDto.getPictureUrl())
                        .role(Role.ROLE_USER)
                        .build())
        );

        return tokenProvider.createToken(user);
    }

    public UserInfoDto getUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://www.googleapis.com/oauth2/v2/userinfo?access_token=" + accessToken;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        RequestEntity<Void> requestEntity = new RequestEntity<>(headers, HttpMethod.GET, URI.create(url));
        ResponseEntity<String> responseEntity = restTemplate.exchange(requestEntity, String.class);

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            String json = responseEntity.getBody();
            Gson gson = new Gson();
            return gson.fromJson(json, UserInfoDto.class);
        }

        throw new RuntimeException("유저 정보를 가져오는데 실패했습니다.");
    }

    public User test(Principal principal) {
        Long id = Long.parseLong(principal.getName());

        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
    }
}

