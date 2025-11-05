package com.example.srvalidator.service;

import org.springframework.stereotype.Service;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import java.io.*;
import java.util.*;

@Service
public class XmlValidatorService {

  // map message-type to local XSD path; put your SR2025 XSDs in these locations
  private static final Map<String,String> XSD_MAP = Map.of(
      "pacs.008", "iso20022/sr2025/pacs.008.001.10.xsd",
      "pacs.009", "iso20022/sr2025/pacs.009.001.10.xsd",
      "pain.001", "iso20022/sr2025/pain.001.001.09.xsd",
      "camt.053", "iso20022/sr2025/camt.053.001.08.xsd"
  );

  public Map<String, Object> validate(String msgType, String xmlContent) {
    Map<String,Object> res = new LinkedHashMap<>();
    try {
      String xsdPath = XSD_MAP.get(msgType);
      if (xsdPath == null) throw new IllegalArgumentException("Unsupported msgType: " + msgType);

      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      Schema schema;
      try (InputStream xsd = getResource(xsdPath)) {
        if (xsd == null) throw new FileNotFoundException("XSD not found: " + xsdPath);
        schema = factory.newSchema(new StreamSource(xsd));
      }

      Validator validator = schema.newValidator();
      try (InputStream xml = new ByteArrayInputStream(xmlContent.getBytes())) {
        validator.validate(new StreamSource(xml));
      }

      res.put("valid", true);
      res.put("details", "Validated against SR2025 schema: " + msgType);
    } catch (Exception e) {
      res.put("valid", false);
      res.put("error", e.getMessage());
    }
    return res;
  }

  private InputStream getResource(String path) {
    // classpath: src/main/resources/<path>
    InputStream cp = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    if (cp != null) return cp;
    // or absolute/relative file:
    try { return new FileInputStream(path); } catch (Exception ignored) {}
    return null;
  }
}
