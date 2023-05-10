package com.roadl.service.impl;

import cn.hutool.cache.Cache;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.roadl.bean.CreateResp;
import com.roadl.bean.PrecreateResp;
import com.roadl.bean.Token;
import com.roadl.service.UploadService;
import com.roadl.util.InputOutput;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartException;
import org.thymeleaf.util.StringUtils;

@Service
@Slf4j
public class UploadServiceImpl implements UploadService {

  @Value("${baidu.pan.clientId}")
  private String BAIDU_PAN_CLIENT_ID ;

  @Value("${baidu.pan.clientSecret}")
  private String BAIDU_PAN_CLIENT_SECRET ;

  @Resource
  Cache<String, String> cache;

  @Value("${recall}")
  private String RECALL;

  @Override
  public boolean up(File f) {
    try {
      // 1. 获取授权，拿到access token
      String paramAccessToken = getAccessTokenFromCache();
      log.info("access token: {}", paramAccessToken);
      String paramFileName = f.getName();
      long paramFileSize = f.length();

      // 2. 或需要切割
      List<File> files = new ArrayList<>();
      int ONE_SIZE = 4 * 1024 * 1024; // 1M = 1024KB = 1024 * 1024 Byte

      if (paramFileSize <= ONE_SIZE) {
        files.add(f);
      } else {
        try {
          files = splitBySize(f, ONE_SIZE);
        } catch (IOException e) {
          e.printStackTrace();
          throw new MultipartException("切割文件异常");
        }
      }
      // 3. 预上传
      String sUrl = String.format("https://pan.baidu.com/rest/2.0/xpan/file?method=precreate&access_token=%s", paramAccessToken);

      String[] md5Files = new String[0];
      MessageDigest digest = MessageDigest.getInstance("MD5"); // https://www.baeldung.com/java-md5-checksum-file

      String paramBody = String.format("path=/apps/roadl/%s&size=%d&isdir=0&rtype=3&autoinit=1&block_list=", paramFileName, paramFileSize);
      StringBuilder sbBlockList = new StringBuilder("["); // /apps/roadl为百度盘中的文件夹

      for (File file : files) {
        log.info("file split: {}", file.getAbsolutePath());
        byte[] data = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
        byte[] hash = digest.digest(data);
        String md5 = new BigInteger(1, hash).toString(16);
        md5Files = ArrayUtils.add(md5Files, md5);

        // block_list=["804c5c9212b2999bcff929828016dc8e","9eb94224cd7a79953791fe44beebd8a0"]
        sbBlockList.append("\"").append(md5).append("\"").append(",");
      }
      sbBlockList.deleteCharAt(sbBlockList.length() - 1); //删除最后的,
      sbBlockList.append("]"); // 末尾加上]
      String paramBlockList = sbBlockList.toString();

      String respPrecreate = reqBaiduBody(sUrl, paramBody + paramBlockList);
      /*
      {
         "uploadid" : "P1-MTAuMTUxLjgxLjE1OjE2Nzg3Nzg3OTM6NTMxODg0OTkzNTA1Nzk0NzY4",
         "return_type" : 1,
         "errno" : 0,
         "request_id" : 531884993505794768,
         "block_list" : [
            0,
            1
         ]
      }
       */
      log.info("precreate, baidu response: {}", respPrecreate);
      PrecreateResp precreateResp = JSONUtil.toBean(respPrecreate, PrecreateResp.class);
      String paramUploadid = precreateResp.getUploadid(); //上传ID

      // 4. 分片上传
      sUrl = String.format("https://d.pcs.baidu.com/rest/2.0/pcs/superfile2?method=upload&access_token=%s&type=tmpfile&uploadid=%s&path=/apps/roadl/%s&partseq="
          , paramAccessToken, paramUploadid, paramFileName);

      for (int i = 0; i < files.size(); i++) {
        String respUpload = reqBaiduFile(sUrl + i, files.get(i));
        /*
        {
           "md5" : "804c5c9212b2999bcff929828016dc8e",
           "request_id" : 1684913449555248535
        }
         */
        log.info("upload, baidu response: {}", respUpload);
      }

      // 5. 创建远程文件
      sUrl = String.format("https://pan.baidu.com/rest/2.0/xpan/file?method=create&access_token=%s", paramAccessToken);
      paramBody = String.format("path=/apps/roadl/%s&size=%d&isdir=0&rtype=3&uploadid=%s&block_list=", paramFileName, paramFileSize, paramUploadid);
      String respCreate = reqBaiduBody(sUrl, paramBody + paramBlockList);
      log.info("create, baidu response: {}", respCreate);
      CreateResp createResp = JSONUtil.toBean(respCreate, CreateResp.class);

      // 6. 删除本地文件
      for (File file : files) {
        file.delete();
      }
      if (f.exists()) {f.delete();}
      return createResp.getErrno() == 0;
    } catch (Exception e){
      e.printStackTrace();
      new MultipartException("执行上传文件至百度异常: " + e.getMessage());
    }
    return false;
  }

  /**
   * 将文件切割为指定大小
   *
   * https://medium.com/@kthsingh.ms/how-to-split-any-large-file-into-multiple-smaller-files-by-size-or-by-number-of-files-using-java-b356c6fb3ecd
   *
   * @param largeFile
   * @param maxChunkSize
   * @return
   * @throws IOException
   */
  private List<File> splitBySize(File largeFile, int maxChunkSize) throws IOException {
    List<File> list = new ArrayList<>();
    String dir = largeFile.getParent();

    try (InputStream in = Files.newInputStream(largeFile.toPath())) {
      final byte[] buffer = new byte[maxChunkSize];
      int dataRead = in.read(buffer);

      while (dataRead > -1) {
        File fileChunk = stageFile(buffer, dataRead, dir);
        list.add(fileChunk);
        dataRead = in.read(buffer);
      }
    }
    return list;
  }
  private File stageFile(byte[] buffer, int length, String dir) throws IOException {
    File outPutFile = File.createTempFile("temp-", "-split", new File(dir));

    try(FileOutputStream fos = new FileOutputStream(outPutFile)) {
      fos.write(buffer, 0, length);
    }
    return outPutFile;
  }

  /**
   * 通过code从百度请求access_token
   * @param code
   * @return
   */
  @Override
  public String getAccessTokenByCode(String code) {
    String sUrl = String.format("https://openapi.baidu.com/oauth/2.0/token?grant_type=%s&code=%s&client_id=%s&client_secret=%s&redirect_uri=%s"
      , "authorization_code" , code, BAIDU_PAN_CLIENT_ID, BAIDU_PAN_CLIENT_SECRET, RECALL);
    // oob模式，手动复制授权码，再请求接口http://localhost:8080/daniel/bd/callback?code=b9242ee6d88348797a5520f221e108be
    return reqToken(sUrl);
  }

  /**
   * 或通过存在的refresh token从百度刷新access_token
   * @return
   */
  @Override
  public String getAccessTokenFromCache() {
    String accessToken = cache.get("access_token");

    if (!StringUtils.isEmpty(accessToken)) {
      return accessToken;
    }
    String refreshToken = cache.get("refresh_token");

    if (StringUtils.isEmpty(refreshToken)) {
      return null;
    }
    String sUrl = String.format("https://openapi.baidu.com/oauth/2.0/token?grant_type=%s&refresh_token=%s&client_id=%s&client_secret=%s"
        , "refresh_token" , refreshToken, BAIDU_PAN_CLIENT_ID, BAIDU_PAN_CLIENT_SECRET);
    return reqToken(sUrl);
  }

  private String reqToken(String sUrl) {
    String resp = reqBaiduUrl(sUrl);
    /* 返回授权
    {
        "expires_in": 2592000, // Access Token的有效期，单位为秒。
        "refresh_token": "122.2959fe0da8c91d522099c5dca1b5608f.YDUwKsc1DS89VaP2DogevEN15cD65vXLtZ7bHHe.DbEWAW", // 用于刷新 Access Token, 有效期为10年。
        "access_token": "121.fd4b4277dba7a65a51cf370d0e83f567.Y74pa1cYlIOT_Vdp2xuWOqeasckh1tWtxT9Ouw5.LPOBOA",
        "session_secret": "",
        "session_key": "",
        "scope": "basic netdisk"
    }
     */
    Token token = JSONUtil.toBean(resp, Token.class);
    log.info(String.format("access_token: %s, expired in: %d", token.getAccessToken(), token.getExpiresIn()));
    cache.put("access_token", token.getAccessToken(), token.getExpiresIn());
    cache.put("refresh_token", token.getRefreshToken(), 103652436001000L); // 10*365*24*3600*1000
    log.info("token from cache: " + cache.get("access_token"));
    log.info("refresh token from cache: {}", cache.get("refresh_token"));
    return resp;
  }

  private String reqBaiduUrl(String sUrl) {
    return reqBaidu(sUrl, "GET", null, null);
  }
  private String reqBaiduBody(String sUrl, String body) {
    return reqBaidu(sUrl, "POST", body, null);
  }
  private String reqBaiduFile(String sUrl, File f) {
    return reqBaidu(sUrl, "POST", null, f);
  }

  /**
   * 请求百度
   * @param sUrl 基础url
   * @param method 方式，一般是GET，或POST
   * @param body 请求的body内容
   * @return
   */
  private String reqBaidu(String sUrl, String method, String body, File f) {
    try {
      log.info("req baidu, method: {}, url: {}, body: {}", method, sUrl, body);
      URL url = new URL(sUrl);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod(method);

      if ("POST".equalsIgnoreCase(method)) {
        conn.setDoOutput(true);
      }
      if (!StringUtils.isEmpty(body)) { // body发参数 https://www.baeldung.com/httpurlconnection-post
        try (OutputStream os = conn.getOutputStream()) {
          byte[] bytesOut = body.getBytes("utf-8");
          os.write(bytesOut, 0, bytesOut.length);
        }
      }
      if (f != null) { // 上传文件 https://www.programming-books.io/essential/android/upload-post-file-using-httpurlconnection-4b647c18f1ab42679a23212cbdb7047d
        String boundary = UUID.randomUUID().toString();
        conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        DataOutputStream os = new DataOutputStream(conn.getOutputStream());

        os.writeBytes("--" + boundary + "\r\n");
        os.writeBytes("Content-Disposition: form-data; name=\"description\"\r\n\r\n");
        os.writeBytes(f.getName() + "\r\n");

        os.writeBytes("--" + boundary + "\r\n");
        os.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + f.getName() + "\"\r\n\r\n");
        os.write(FileUtil.readBytes(f));
        os.writeBytes("\r\n");

        os.writeBytes("--" + boundary + "--\r\n");
        os.flush();
      }
      final int status = conn.getResponseCode();

      // https://github.com/javamelody/javamelody/blob/master/javamelody-core/src/main/java/net/bull/javamelody/internal/model/LabradorRetriever.java#L262
      if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
        final String error = InputOutput.pumpToString(conn.getErrorStream(),
            Charset.forName("UTF-8"));
        final String msg = "Error connecting to " + url + '(' + status + "): " + error;
        log.error("req baidu error: {}", msg);
        conn.disconnect();
        throw new IOException(msg);
      } else {
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuffer response = new StringBuffer();
        String inputLine;

        while ((inputLine = in.readLine()) != null) {
          response.append(inputLine);
        }
        in.close();
        String resp = response.toString();
        conn.disconnect();
        return resp;
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new MultipartException("请求百度异常：" + e.getMessage());
    }
  }
}
