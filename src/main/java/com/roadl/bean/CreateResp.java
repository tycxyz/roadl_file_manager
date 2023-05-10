package com.roadl.bean;

import lombok.Data;

@Data
public class CreateResp {
  int errno;
  String path;
  String request_id;
  String md5;
}
