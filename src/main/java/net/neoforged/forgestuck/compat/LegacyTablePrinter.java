package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class LegacyTablePrinter<T> {
    private final List<Header<T>> headers = new ArrayList<>();
    private final List<T> rows = new ArrayList<>();

    public LegacyTablePrinter<T> header(String name, Function<T, String> value) {
        return header(name, value, false);
    }

    public LegacyTablePrinter<T> header(String name, Function<T, String> value, boolean rightAligned) {
        headers.add(new Header<>(name, value, rightAligned));
        return this;
    }

    public LegacyTablePrinter<T> add(T row) {
        rows.add(row);
        return this;
    }

    public LegacyTablePrinter<T> add(Collection<? extends T> values) {
        values.forEach(this::add);
        return this;
    }

    public void clearRows() {
        rows.clear();
    }

    public void build(StringBuilder output) {
        int[] widths = new int[headers.size()];
        int totalWidth = 1;
        for (int column = 0; column < widths.length; column++) {
            widths[column] = headers.get(column).name.length() + 2;
        }
        for (T row : rows) {
            for (int column = 0; column < widths.length; column++) {
                widths[column] = Math.max(widths[column], headers.get(column).value.apply(row).length() + 2);
            }
        }
        for (int width : widths) totalWidth += width + 3;

        line(output, totalWidth);
        for (int column = 0; column < widths.length; column++) {
            Header<T> header = headers.get(column);
            int left = (widths[column] - header.name.length()) / 2;
            output.append("| ");
            spaces(output, left);
            output.append(header.name);
            spaces(output, widths[column] - header.name.length() - left);
            output.append(' ');
        }
        output.append("|\n");
        line(output, totalWidth);
        for (T row : rows) {
            for (int column = 0; column < widths.length; column++) {
                Header<T> header = headers.get(column);
                String value = header.value.apply(row);
                int padding = widths[column] - value.length();
                output.append("| ");
                if (header.rightAligned) spaces(output, padding);
                output.append(value);
                if (!header.rightAligned) spaces(output, padding);
                output.append(' ');
            }
            output.append("|\n");
        }
        line(output, totalWidth);
    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder();
        build(output);
        return output.toString();
    }

    private static void line(StringBuilder output, int width) {
        output.append('|');
        spaces(output, width - 2);
        output.append("|\n");
    }

    private static void spaces(StringBuilder output, int count) {
        output.append(" ".repeat(count));
    }

    private record Header<T>(String name, Function<T, String> value, boolean rightAligned) {}
}
