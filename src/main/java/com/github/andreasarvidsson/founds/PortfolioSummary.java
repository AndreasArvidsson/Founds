package com.github.andreasarvidsson.founds;

import com.github.andreasarvidsson.founds.util.BaseUtil;
import com.github.andreasarvidsson.founds.util.Comparison;
import com.github.andreasarvidsson.founds.util.Sum;
import com.github.andreasarvidsson.founds.util.Table;
import com.github.andreasarvidsson.founds.util.Values;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Andreas Arvidsson
 */
public class PortfolioSummary extends BaseUtil {

    private final static String SPACE = "   |   ";
    private final String name;
    private final List<FoundData> founds;
    private double percentageSum, avgFee, risk;
    final Values companiesSize = new Values();
    final Values countries = new Values();
    final Values sectors = new Values();
    final Values regions = new Values();
    final Sum sum = new Sum();
    final Sum developments = new Sum();

    public static PortfolioSummary create(
            final String name,
            final List<SelectedFound> selectedFounds) throws IOException {
        return create(name, selectedFounds, false);
    }

    public static PortfolioSummary createInclMorningstar(
            final String name,
            final List<SelectedFound> selectedFounds) throws IOException {
        return create(name, selectedFounds, true);
    }

    public static PortfolioSummary create(
            final String name,
            final List<SelectedFound> selectedFounds,
            final boolean inclMorningstarData) throws IOException {
        final List<FoundData> founds = new ArrayList();
        double sum = 0.0;
        for (final SelectedFound sf : selectedFounds) {
            sum += sf.percentage;
        }
        for (final SelectedFound sd : selectedFounds) {
            founds.add(new FoundData(
                    sd.percentage,
                    sd.percentage / sum,
                    Avanza.getFound(sd.name, sd.alternativeNames),
                    inclMorningstarData ? Morningstar.getFound(sd.name, sd.alternativeNames) : null
            ));
        }
        return new PortfolioSummary(name, founds);
    }

    private PortfolioSummary(
            final String name,
            final List<FoundData> founds) throws IOException {
        this.name = name;
        this.founds = founds;
        for (final FoundData fd : founds) {
            percentageSum += fd.percentage * 100;
            avgFee += fd.avanza.productFee * fd.percentageNormalized;
            risk += fd.avanza.risk * fd.percentageNormalized;
            if (fd.avanza.sharpeRatio != null) {
                sum.add(Headers.SHARP_RATIO, fd.avanza.sharpeRatio, fd.percentageNormalized);
            }
            Headers.DEVELOPMENT_TITLES.forEach(key -> {
                if (fd.avanza.hasDevelopment(key)) {
                    developments.add(key, fd.avanza.getDevelopment(key), fd.percentageNormalized);
                }
            });
            fd.avanza.countryChartData.forEach(data -> {
                countries.add(data.name, data.y * fd.percentageNormalized);
            });
            fd.avanza.regionChartData.forEach(data -> {
                regions.add(data.name, data.y * fd.percentageNormalized);
            });
            fd.avanza.sectorChartData.forEach(data -> {
                sectors.add(data.name, data.y * fd.percentageNormalized);
            });
            if (fd.morningstar != null) {
                companiesSize.add("Stora bolag", fd.morningstar.largeCompanies * fd.percentageNormalized);
                companiesSize.add("Medelstora bolag", fd.morningstar.middleCompanies * fd.percentageNormalized);
                companiesSize.add("Små bolag", fd.morningstar.smallCompanies * fd.percentageNormalized);
                final double swePercentage = fd.avanza.getRegion(Regions.SWEDEN) / 100;
                companiesSize.add("Stora svenska bolag", fd.morningstar.largeCompanies * fd.percentageNormalized * swePercentage);
                companiesSize.add("Medelstora svenska bolag", fd.morningstar.middleCompanies * fd.percentageNormalized * swePercentage);
                companiesSize.add("Små svenska bolag", fd.morningstar.smallCompanies * fd.percentageNormalized * swePercentage);
            }
        }
        countries.compile(true);
        regions.compile(true);
        sectors.compile(true);
        companiesSize.compile();
        sum.compile();
        developments.compile();
    }

    public void print() {
        System.out.printf(
                "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
                + " %s "
                + ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n\n",
                name
        );
        final Table table = new Table();
        addHeaders(table);
        addFounds(table);
        addSum(table);
        table.print();
        printChartTable();
    }

    private void addHeaders(final Table table) {
        table.addRow(0,
                "Namn", "Andel (%)", "Avgift (%)", "Risk", Headers.SHARP_RATIO,
                "Kategorier", "Sverige (%)", "Asien (%)"
        );
        if (!companiesSize.isEmpty()) {
            table.addRow(0,
                    "Stora (%)", "Medelstora (%)", "Små (%)"
            );
        }
        Headers.DEVELOPMENT_TITLES.forEach(title -> {
            if (developments.has(title)) {
                table.addRow(0, title);
            }
        });
        table.addHR();
    }

    private void addFounds(final Table table) {
        founds.forEach(fd -> {
            final AvanzaFound found = fd.avanza;
            final int rowIndex = table.numRows();
            table.addRow(rowIndex,
                    found.name,
                    format(fd.percentage * 100),
                    format(found.productFee),
                    Integer.toString(found.risk),
                    found.sharpeRatio != null ? format(found.sharpeRatio) : "-",
                    String.join(", ", found.categories),
                    format(fd.avanza.getRegion(Regions.SWEDEN)),
                    format(fd.avanza.getRegion(Regions.ASIA))
            );
            if (!companiesSize.isEmpty()) {
                if (fd.morningstar != null) {
                    table.addRow(rowIndex,
                            format(fd.morningstar.largeCompanies),
                            format(fd.morningstar.middleCompanies),
                            format(fd.morningstar.smallCompanies)
                    );
                }
                else {
                    table.addRow(rowIndex, "", "", "");
                }
            }
            Headers.DEVELOPMENT_TITLES.forEach(key -> {
                if (found.hasDevelopment(key)) {
                    table.addRow(rowIndex, format(found.getDevelopment(key)));
                }
                else {
                    table.addRow(rowIndex, "-");
                }
            });
        });
        table.addHR();
    }

    private void addSum(final Table table) {
        final int rowIndex = table.numRows();
        table.addRow(rowIndex,
                "",
                format(percentageSum),
                format(avgFee),
                format(risk),
                format(sum.get(Headers.SHARP_RATIO)),
                "",
                format(regions.get(Regions.SWEDEN)),
                format(regions.get(Regions.ASIA))
        );
        if (!companiesSize.isEmpty()) {
            table.addRow(rowIndex,
                    format(companiesSize.get(0).second()),
                    format(companiesSize.get(1).second()),
                    format(companiesSize.get(2).second())
            );
        }
        Headers.DEVELOPMENT_TITLES.forEach(title -> {
            if (developments.has(title)) {
                table.addRow(rowIndex, format(developments.get(title)));
            }
        });
    }

    private void printChartTable() {
        final Table table = new Table();
        table.addRow(0,
                "Land", "Andel (%)", SPACE,
                "Region", "Andel (%)", SPACE,
                "Bransch", "Andel (%)"
        );
        if (!companiesSize.isEmpty()) {
            table.addRow(0,
                    SPACE, "Storlek", "Andel (%)"
            );
        }
        table.addHR();
        final int size = Math.min(
                10,
                max(countries.size(), regions.size(), sectors.size(), companiesSize.size())
        );
        for (int i = 0; i < size; ++i) {
            final int rowIndex = table.numRows();
            table.addRow(rowIndex,
                    i < countries.size() ? countries.get(i).first() : "",
                    i < countries.size() ? format(countries.get(i).second()) : "",
                    SPACE,
                    i < regions.size() ? regions.get(i).first() : "",
                    i < regions.size() ? format(regions.get(i).second()) : "",
                    SPACE,
                    i < sectors.size() ? sectors.get(i).first() : "",
                    i < sectors.size() ? format(sectors.get(i).second()) : ""
            );
            if (!companiesSize.isEmpty()) {
                table.addRow(rowIndex,
                        SPACE,
                        i < companiesSize.size() ? companiesSize.get(i).first() : "",
                        i < companiesSize.size() ? format(companiesSize.get(i).second()) : ""
                );
            }
        }
        table.print();
    }

    public void compare(final PortfolioSummary summary) {
        System.out.printf(
                "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
                + " %s vs %s "
                + ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n\n",
                name, summary.name
        );
        final Table table = new Table();
        addBasicCompareTable(summary, table);
        addChartCompareTables(summary, table);
        table.print();
    }

    private void addBasicCompareTable(final PortfolioSummary summary, final Table table) {
        final List<String> headers = new ArrayList(Arrays.asList(
                "", name, summary.name, "Skillnad", SPACE
        ));
        final List<List<String>> rows = new ArrayList();
        rows.add(new ArrayList(Arrays.asList(
                "# Fonder",
                Integer.toString(founds.size()),
                Integer.toString(summary.founds.size()),
                Integer.toString(summary.founds.size() - founds.size()),
                SPACE
        )));
        addRow(
                rows, true, 1, "Andel (%)", percentageSum, summary.percentageSum
        );
        addRow(
                rows, true, 2, "Avgift (%)", avgFee, summary.avgFee
        );
        addRow(
                rows, true, 3, "Risk", risk, summary.risk
        );
        addRow(
                rows, true, 4, Headers.SHARP_RATIO, sum.get(Headers.SHARP_RATIO), summary.sum.get(Headers.SHARP_RATIO)
        );
        if (!companiesSize.isEmpty() && !summary.companiesSize.isEmpty()) {
            headers.addAll(Arrays.asList(
                    "Storlek",
                    String.format("%s (%%)", name),
                    String.format("%s (%%)", summary.name),
                    "Skillnad (%)"
            ));
            compareValues(rows, false, companiesSize, summary.companiesSize);
        }
        table.addRow(headers);
        table.addHR();
        rows.forEach(row -> {
            table.addRow(row);
        });
        table.addRow("");
    }

    private void addChartCompareTables(final PortfolioSummary summary, final Table table) {
        final List<List<String>> rows = new ArrayList();
        compareValues(rows, true, countries, summary.countries);
        compareValues(rows, false, regions, summary.regions);
        addHeaderRow(table, summary, "Land", "Region");
        rows.forEach(row -> {
            table.addRow(row);
        });
        table.addRow("");
        rows.clear();
        compareValues(rows, true, sectors, summary.sectors);
        compareDevelopments(rows, false, summary);
        addHeaderRow(table, summary, "Bransch", "Utveckling");
        rows.forEach(row -> {
            table.addRow(row);
        });
    }

    private void addHeaderRow(
            final Table table,
            final PortfolioSummary summary,
            final String title1, final String title2) {
        table.addRow(
                title1,
                String.format("%s (%%)", name),
                String.format("%s (%%)", summary.name),
                "Skillnad (%)",
                SPACE,
                title2,
                String.format("%s (%%)", name),
                String.format("%s (%%)", summary.name),
                "Skillnad (%)"
        );
        table.addHR();
    }

    private void compareDevelopments(
            final List<List<String>> rows,
            final boolean first,
            final PortfolioSummary summary) {
        for (int i = 0; i < Headers.DEVELOPMENT_TITLES.size(); ++i) {
            final String title = Headers.DEVELOPMENT_TITLES.get(i);
            if (developments.has(title) && summary.developments.has(title)) {
                addRow(rows, first, i, title,
                        developments.get(title),
                        summary.developments.get(title)
                );
            }
        }
    }

    private void compareValues(
            final List<List<String>> rows,
            final boolean first,
            final Values values1,
            final Values values2) {
        final Comparison comparison = new Comparison();
        values1.forEach(p -> {
            comparison.putFirst(p.first(), p.second());
        });
        values2.forEach(p -> {
            comparison.putSecond(p.first(), p.second());
        });
        comparison.compile();
        for (int i = 0; i < comparison.size() && i < 10; ++i) {
            final String key = comparison.get(i);
            addRow(
                    rows, first, i, key, comparison.first(key), comparison.second(key)
            );
        }
    }

    final void addRow(
            final List<List<String>> rows,
            final boolean first,
            final int i,
            final String title,
            final double val1,
            final double val2) {
        if (i >= rows.size()) {
            rows.add(new ArrayList());
            if (!first) {
                rows.get(i).addAll(Arrays.asList("", "", "", "", SPACE));
            }
        }
        rows.get(i).addAll(Arrays.asList(
                title,
                format(val1),
                format(val2),
                format(val2 - val1)
        ));
        if (first) {
            rows.get(i).add(SPACE);
        }
    }

}
