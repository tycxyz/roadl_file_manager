package com.roadl.bean;

import lombok.Data;

@Data
public class Token {
  private int expiresIn;
  private String refreshToken;
  private String accessToken;
}
