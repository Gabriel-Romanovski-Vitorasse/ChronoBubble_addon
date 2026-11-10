package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tela de configura\u00e7\u00e3o do Chrono Bubble. Aberta por Mods > Chrono Bubble > Config ou pela tecla K.
 * Uma p\u00e1gina por grupo de op\u00e7\u00f5es (no m\u00e1ximo 7 por p\u00e1gina). Salvar grava o arquivo .toml e aplica na hora.
 */
public class ChronoConfigScreen extends Screen {
    private static final int ROWS_PER_PAGE = 7;

    private final Screen parent;
    private final List<List<ChronoConfig.Opt>> pages = new ArrayList<>();
    private int page;
    private final Map<ChronoConfig.Opt, Boolean> pendingBool = new HashMap<>();
    private final Map<ChronoConfig.Opt, String> pendingText = new HashMap<>();
    private String status = "";

    public ChronoConfigScreen(Screen parent) {
        super(new StringTextComponent("Chrono Bubble - Configura\u00e7\u00e3o"));
        this.parent = parent;
        String lastTitle = null;
        List<ChronoConfig.Opt> cur = null;
        for (ChronoConfig.Opt o : ChronoConfig.OPTS) {
            if (cur == null || !o.sectionTitle.equals(lastTitle) || cur.size() >= ROWS_PER_PAGE) {
                cur = new ArrayList<>();
                pages.add(cur);
                lastTitle = o.sectionTitle;
            }
            cur.add(o);
        }
    }

    // ---------- valores ----------

    private static String fmt(ChronoConfig.Opt o, Object v) {
        if (o.kind == ChronoConfig.Opt.INT) return String.valueOf(((Number) v).intValue());
        return String.format(Locale.ROOT, "%s", trimZeros(((Number) v).doubleValue()));
    }

    private static String trimZeros(double d) {
        String s = String.format(Locale.ROOT, "%.3f", d);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", ".0");
        return s;
    }

    private boolean boolOf(ChronoConfig.Opt o) {
        Boolean p = pendingBool.get(o);
        return p != null ? p : (Boolean) o.value.get();
    }

    private String textOf(ChronoConfig.Opt o) {
        String p = pendingText.get(o);
        return p != null ? p : fmt(o, o.value.get());
    }

    private static String onOff(boolean b) {
        return b ? "\u00a7aLigado" : "\u00a7cDesligado";
    }

    // ---------- layout ----------

    @Override
    protected void init() {
        int controlX = this.width / 2 + 30;
        int y0 = 52;
        List<ChronoConfig.Opt> list = pages.get(page);
        for (int i = 0; i < list.size(); i++) {
            final ChronoConfig.Opt o = list.get(i);
            int y = y0 + i * 24;
            if (o.kind == ChronoConfig.Opt.BOOL) {
                this.addButton(new Button(controlX, y, 150, 20, new StringTextComponent(onOff(boolOf(o))), b -> {
                    pendingBool.put(o, !boolOf(o));
                    b.setMessage(new StringTextComponent(onOff(boolOf(o))));
                }));
            } else {
                TextFieldWidget tf = new TextFieldWidget(this.font, controlX, y, 150, 20, StringTextComponent.EMPTY);
                tf.setFilter(o.kind == ChronoConfig.Opt.INT ? s -> s.matches("[0-9-]*") : s -> s.matches("[0-9.,-]*"));
                tf.setValue(textOf(o));
                tf.setResponder(s -> pendingText.put(o, s));
                this.addButton(tf);
            }
        }

        int by = this.height - 52;
        this.addButton(new Button(this.width / 2 - 155, by, 100, 20, new StringTextComponent("< Anterior"), b -> changePage(-1)));
        this.addButton(new Button(this.width / 2 + 55, by, 100, 20, new StringTextComponent("Pr\u00f3xima >"), b -> changePage(1)));
        this.addButton(new Button(this.width / 2 - 155, this.height - 28, 100, 20, new StringTextComponent("Salvar e fechar"), b -> save()));
        this.addButton(new Button(this.width / 2 - 50, this.height - 28, 100, 20, new StringTextComponent("Restaurar p\u00e1gina"), b -> resetPage()));
        this.addButton(new Button(this.width / 2 + 55, this.height - 28, 100, 20, new StringTextComponent("Cancelar"), b -> this.minecraft.setScreen(parent)));
    }

    private void changePage(int delta) {
        page = (page + delta + pages.size()) % pages.size();
        status = "";
        this.init(this.minecraft, this.width, this.height);
    }

    private void resetPage() {
        for (ChronoConfig.Opt o : pages.get(page)) {
            if (o.kind == ChronoConfig.Opt.BOOL) pendingBool.put(o, (Boolean) o.def);
            else pendingText.put(o, fmt(o, o.def));
        }
        status = "\u00a77P\u00e1gina restaurada ao padr\u00e3o (clique em Salvar para gravar).";
        this.init(this.minecraft, this.width, this.height);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void save() {
        // valida tudo antes de gravar
        Map<ChronoConfig.Opt, Object> toSet = new HashMap<>();
        for (Map.Entry<ChronoConfig.Opt, Boolean> e : pendingBool.entrySet()) toSet.put(e.getKey(), e.getValue());
        for (Map.Entry<ChronoConfig.Opt, String> e : pendingText.entrySet()) {
            ChronoConfig.Opt o = e.getKey();
            try {
                double d = Double.parseDouble(e.getValue().trim().replace(',', '.'));
                d = Math.max(o.min, Math.min(o.max, d));
                toSet.put(o, o.kind == ChronoConfig.Opt.INT ? (Object) Integer.valueOf((int) Math.round(d)) : (Object) Double.valueOf(d));
            } catch (NumberFormatException ex) {
                status = "\u00a7cValor inv\u00e1lido em: " + o.label;
                return;
            }
        }
        try {
            for (Map.Entry<ChronoConfig.Opt, Object> e : toSet.entrySet()) {
                ((ForgeConfigSpec.ConfigValue) e.getKey().value).set(e.getValue());
            }
            ChronoConfig.SPEC.save();
            ChronoConfig.apply();
        } catch (Throwable t) {
            status = "\u00a7cN\u00e3o consegui salvar: " + t.getClass().getSimpleName();
            return;
        }
        this.minecraft.setScreen(parent);
    }

    // ---------- desenho ----------

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partial) {
        this.renderBackground(ms);
        drawCenteredString(ms, this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        List<ChronoConfig.Opt> list = pages.get(page);
        drawCenteredString(ms, this.font, new StringTextComponent("\u00a7b" + list.get(0).sectionTitle
                + " \u00a77(" + (page + 1) + "/" + pages.size() + ")"), this.width / 2, 30, 0xFFFFFF);

        int labelX = this.width / 2 - 190;
        int y0 = 52;
        for (int i = 0; i < list.size(); i++) {
            ChronoConfig.Opt o = list.get(i);
            drawString(ms, this.font, o.label, labelX, y0 + i * 24 + 6, 0xE0E0E0);
        }
        drawCenteredString(ms, this.font, new StringTextComponent(status), this.width / 2, this.height - 66, 0xFFFFFF);
        super.render(ms, mouseX, mouseY, partial);

        // dica ao passar o mouse sobre o nome da op\u00e7\u00e3o
        for (int i = 0; i < list.size(); i++) {
            int y = y0 + i * 24;
            if (mouseX >= labelX && mouseX < this.width / 2 + 28 && mouseY >= y && mouseY < y + 20) {
                String tip = list.get(i).comment + (list.get(i).kind == ChronoConfig.Opt.BOOL ? ""
                        : "  [" + trimZeros(list.get(i).min) + " a " + trimZeros(list.get(i).max) + "]");
                this.renderTooltip(ms, this.font.split(new StringTextComponent(tip), 220), mouseX, mouseY);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
