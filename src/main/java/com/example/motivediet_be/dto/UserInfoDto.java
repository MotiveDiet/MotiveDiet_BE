package com.example.motivediet_be.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class UserInfoDto {

    private String id;
    private String email;

    @SerializedName("verified_email")
    private Boolean verifiedEmail;

    private String name;

    @SerializedName("given_name")
    private String givenName;

    @SerializedName("family_name")
    private String familyName;

    @SerializedName("picture") // 구글이 내려주는 JSON 키 이름이 picture 이므로 그에 맞춰 매핑해야 한다
    private String pictureUrl;

    private String locale;
}
