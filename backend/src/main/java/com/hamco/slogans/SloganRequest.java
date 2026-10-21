package com.hamco.slogans;

/**
 * The ASP.NET update accepts either field independently; Boolean (rather
 * than primitive boolean) distinguishes “false” from “field not supplied”.
 */
public record SloganRequest(String text, Boolean isActive) {
}
