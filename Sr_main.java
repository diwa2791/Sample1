package com.example.srvalidator.web;

import com.example.srvalidator.service.XmlValidatorService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class ValidationController {

  private final XmlValidatorService service;

  public ValidationController(XmlValidatorService service) {
    this.service = service;
  }

  @PostMapping(value="/validate", consumes=MediaType.APPLICATION_JSON_VALUE,
               produces=MediaType.APPLICATION_JSON_VALUE)
  public Map<String,Object> validate(@RequestBody Map<String,String> req) {
    String msgType = req.get("msgType");
    String xml     = req.get("xml");
    return service.validate(msgType, xml);
  }
}
