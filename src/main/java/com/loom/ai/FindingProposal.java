package com.loom.ai;

public record FindingProposal(
        String type,   // insight | pattern | constraint | tension | question | artifact
        String title,
        String body
) {}
