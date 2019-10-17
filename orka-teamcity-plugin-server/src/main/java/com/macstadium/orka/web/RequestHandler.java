package com.macstadium.orka.web;

import java.util.Map;

import org.jdom.Element;

public interface RequestHandler {
    Element handle(Map<String, String> params);
}