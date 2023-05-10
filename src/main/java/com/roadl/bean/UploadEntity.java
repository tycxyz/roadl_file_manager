package com.roadl.bean;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadEntity {
  String password;
  String fileName;
  MultipartFile file;
}
