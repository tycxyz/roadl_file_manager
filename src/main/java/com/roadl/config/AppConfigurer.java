package com.roadl.config;


import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class AppConfigurer implements WebMvcConfigurer {
  //    本地路径
  @Value("${dir}")
  private String DIR;
  //    对外虚拟路径
  @Value("${staticAccessPath}")
  private String staticAccessPath;

  @Value("${minio.url}")
  private String MINIO_URL;

  @Value("${minio.appId}")
  private String MINIO_APPID;

  @Value("${minio.appSecret}")
  private String MINIO_APPSECRET;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler(staticAccessPath).addResourceLocations("file:"+ DIR);
  }

  @Bean
  public Cache cache(){
    Cache<String, String> timedCache = CacheUtil.newTimedCache(2592000); // 默认2592000秒失效
    return timedCache;
  }

  @Bean(name="minioClient")
  public MinioClient minioClient() {
    try {
      return new MinioClient(MINIO_URL, MINIO_APPID, MINIO_APPSECRET);
    } catch (Exception e) {
      e.printStackTrace();
      log.error("初始化时，无法建立minio服务器的连接");
      return null;
    }
  }
}
