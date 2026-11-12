package com.devcord.compilerservice;

public class CodeRequest {
    private String language;
    private String code;
    private String stdin;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }

    public String getInput() {
        return stdin;
    }

    public void setInput(String input) {
        this.stdin = input;
    }
}