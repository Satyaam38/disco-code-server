package com.devcord.compilerservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class ExecutionController {

    @Autowired
    private CodeExecutionService codeExecutionService;

    @PostMapping("/execute")
    public Map<String, Object> executeCode(@RequestBody CodeRequest request) {
        return codeExecutionService.execute(request.getLanguage(), request.getCode());
    }
}