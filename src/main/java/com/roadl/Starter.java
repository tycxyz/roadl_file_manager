package com.roadl;


import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ServletComponentScan
@EnableAsync
public class Starter extends org.springframework.boot.web.servlet.support.SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Starter.class);
    }

    public static void main(String[] args) {
      System.setProperty("es.set.netty.runtime.available.processors", "false");
        SpringApplication.run(Starter.class, args);
    }

    // Tomcat large file upload connection
    // reset TomcatServletWebServerFactory() 方法主要是为了解决上传文件大于 10M (上面所设置)出现连接重置的问题，
    // 此异常内容 GlobalException 也捕获不到
    // 参考自：https://www.jianshu.com/p/c85b4cffcf0f
    @Bean
    public TomcatServletWebServerFactory tomcatEmbedded() {
      TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
      tomcat.addConnectorCustomizers((TomcatConnectorCustomizer) connector -> {
        if ((connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?>)) {
          //-1 means unlimited
          ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setMaxSwallowSize(-1);
        }
      });
      return tomcat;
    }
}
