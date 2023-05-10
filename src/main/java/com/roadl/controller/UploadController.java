package com.roadl.controller;

import cn.hutool.cache.Cache;
import com.roadl.bean.UploadEntity;
import com.roadl.service.UploadService;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.util.StringUtils;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Controller
@Slf4j
public class UploadController {
  @Value("${baidu.pan.deviceId}")
  private String BAIDU_PAN_DEVICE_ID ;

  @Value("${baidu.pan.clientId}")
  private String BAIDU_PAN_CLIENT_ID ;

  // 本地路径
  @Value("${dir}")
  private String DIR;

  // 上传时的暗号
  @Value("${hint}")
  private String HINT;

  // 对外虚拟路径
  @Value("${staticAccessPath}")
  private String staticAccessPath ;

  @Value("${minio.bucket}")
  private String MINIO_BUCKET ;

  @Value("${minio.picPreUrl}")
  private String MINIO_PIC_PRE_URL ;

  @Resource
  private MinioClient minioClient;

  @Resource
  private UploadService uploadService;

  @Resource
  Cache<String, String> cache;

  @Value("${recall}")
  private String RECALL;

  // 添加Thymeleaf模板的映射
  @GetMapping("/upload")
  public String index(Model model){
    boolean existAccessToken = !StringUtils.isEmpty(uploadService.getAccessTokenFromCache());
    // https://www.thymeleaf.org/doc/articles/springmvcaccessdata.html
    model.addAttribute("baiduAuthUrl"
        , String.format("http://openapi.baidu.com/oauth/2.0/authorize?response_type=code&client_id=%s&scope=basic,netdisk&device_id=%s&redirect_uri=%s",
            BAIDU_PAN_CLIENT_ID, BAIDU_PAN_DEVICE_ID, RECALL));
    model.addAttribute("existAccessToken", existAccessToken);
    return "upload";
  }

  @GetMapping("/uploadStatus")
  public String uploadStatus(){
    return "uploadStatus";
  }

  // 请求百度网盘code后，被回调
  // 当使用@Controller 注解时，spring默认方法返回的是view对象（页面）。而加上@ResponseBody，则方法返回的就是具体对象了。
  @ResponseBody
  @GetMapping("/bd/callback")
  public void upload(@RequestParam Map<String, String> params) {
    log.info("BAIDU call back successfully.");

    if (params != null && !params.isEmpty()) {
      if  (params.containsKey("code")) {
        uploadService.getAccessTokenByCode(params.get("code"));
      }
    }
  }

  // 上传文件的post请求
  @PostMapping("/upload")
  public String upload(
// 无法通过@RequestBody来上传文件，同时还有其它参数的解决办法：
// https://stackoverflow.com/questions/48051177/content-type-multipart-form-databoundary-charset-utf-8-not-supported
//          @RequestParam("file") MultipartFile file
//          , @RequestParam("password") String password
//          , @RequestParam(required = false) String fileName
      @ModelAttribute UploadEntity entity
          , RedirectAttributes redirect){
    MultipartFile file = entity.getFile();
    String password = entity.getPassword();
    String fileName = entity.getFileName();

    if (file.isEmpty()){
      redirect.addFlashAttribute("message","Error: No file！");
    } else if (!HINT.equals(password)) {
      redirect.addFlashAttribute("message","Error: Password is wrong！");
    } else {
      try{
        byte[] fileBytes = file.getBytes();
        String type[] = file.getContentType().split("/");

        // 如果未传文件名，则用md5生成
        if (StringUtils.isEmpty(fileName)) {
          InputStream stream = file.getInputStream();
          String fileNamePart = DigestUtils.md5DigestAsHex(stream);
          fileName = fileNamePart + "." + type[1];
        }
        Path path = Paths.get(DIR + fileName);
        // 保存至本地
        Files.write(path, fileBytes);
        File f = new File(path.toString());
        log.info("成功保存至本地" + f.getAbsolutePath());

        // 写入远程
        // putObject(String bucketName, String objectName, InputStream stream, Long size, Map<String, String> headerMap,
        //    ServerSideEncryption sse, String contentType)
        minioClient.putObject(MINIO_BUCKET, fileName, new FileInputStream(f), (long)fileBytes.length, null, null, null);
        log.info("成功保存到minio");

        // https://min.io/docs/minio/linux/developers/java/API.html
//        minioClient.putObject(
//            PutObjectArgs.builder().bucket(MINIO_BUCKET).object(fileName).stream(
//                stream, -1, 10485760)
//                //.contentType("video/mp4")
//                .build());
        // 上传至百度网盘
        boolean isUploadBaidu = uploadService.up(f);

        redirect.addFlashAttribute("message","Upload successful! upload BAIDU "+ isUploadBaidu + ", The file name is: " + fileName);
        redirect.addFlashAttribute("imgUrl", staticAccessPath.replace("**",fileName) );
        redirect.addFlashAttribute("minioUrl", MINIO_PIC_PRE_URL + fileName);
      } catch (Exception e){
        e.printStackTrace();
      }
    }
    return "redirect:uploadStatus"; // 这个不会加context path
    // https://stackoverflow.com/questions/27843540/context-path-with-url-pattern-redirect-in-spring
    //return new RedirectView("/uploadStatus", true);
  }
}
