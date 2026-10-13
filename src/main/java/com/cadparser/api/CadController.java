package com.cadparser.api;

import com.cadparser.service.CadParseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/cad")
public class CadController {

    private final CadParseService cadParseService;

    public CadController(CadParseService cadParseService) {
        this.cadParseService = cadParseService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "dwg", cadParseService.dwgStatus()
        );
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CadParseResponse parse(@RequestPart("file") MultipartFile file) {
        return CadParseResponse.from(cadParseService.parse(file));
    }
}
