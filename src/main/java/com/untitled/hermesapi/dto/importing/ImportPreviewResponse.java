package com.untitled.hermesapi.dto.importing;

import java.util.List;

public record ImportPreviewResponse(
        String format,
        List<ImportRow> rows,
        List<String> warnings
) {
}
