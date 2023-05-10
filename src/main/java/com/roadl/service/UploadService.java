package com.roadl.service;

import java.io.File;

public interface UploadService {

  boolean up(File f);

  String getAccessTokenByCode(String code);

  String getAccessTokenFromCache();
}
