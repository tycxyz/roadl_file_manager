package com.roadl.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 参考自：https://ithelp.ithome.com.tw/articles/10240823
 */
@Controller
public class AppController {
  @GetMapping("/hello")
  public String hello(Model model) {
    model.addAttribute("hello", "Hello World!!!"); // （變數名稱，變數值)
    return "hello"; // 要導入的html
  }
}
