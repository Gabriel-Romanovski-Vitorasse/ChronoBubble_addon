package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tela de configuração do Chrono Bubble. Aberta por Mods > Chrono Bubble > Config ou pela tecla K.
 * Uma página por grupo de opções (no máximo 7 por página). Salvar grava o arquivo .toml e vale na hora.
 * <p>
 * As regras do servidor só podem ser mudadas aqui num mundo aberto neste computador (singleplayer/LAN). Num
 * servidor de outra pessoa elas aparecem só para leitura, com os valores que o servidor mandou; fora de um mundo,
 * aparecem os valores padrão. As opções visuais (deste computador) podem ser mudadas sempre.
 */
public class ChronoConfigScreen extends Screen {
    private static final int ROWS_PER_PAGE = 7;
    private static final int CONTROL_W = 150;
    private static final int ROW_H = 24;
    private static final int TOP = 52;

    private final Screen parent;
    private final List<List<ChronoConfig.Opt>> pages = new ArrayList<>();
    private int page;
    private final Map<ChronoConfig.Opt, Boolean> pendingBool = new HashMap<>();
    private final Map<ChronoConfig.Opt, String> pendingText = new HashMap<>();
    private ITextComponent status = StringTextComponent.EMPTY;
    private int labelX, controlX;

    public ChronoConfigScreen(Screen parent) {
        super(new TranslationTextComponent("gui.chrono_bubble.title"));
        this.parent = parent;
        String lastSection = null;
        List<ChronoConfig.Opt> cur = null;
        for (ChronoConfig.Opt o : ChronoConfig.OPTS) {
            if (cur == null || !o.section.equals(lastSection) || cur.size() >= ROWS_PER_PAGE) {
                cur = new ArrayList<>();
                pages.add(cur);
                lastSection = o.section;
            }
            cur.add(o);
        }
    }

    // ---------- permissão ----------

    /** Regras do servidor só são editáveis com o servidor rodando neste computador (singleplayer ou LAN aberta aqui). */
    private boolean serverEditable() {
        return this.minecraft != null && this.minecraft.hasSingleplayerServer() && ChronoConfig.SERVER_SPEC.isLoaded();
    }

    private boolean editable(ChronoConfig.Opt o) {
        return !o.server || serverEditable();
    }

    // ---------- valores ----------

    private static String fmt(ChronoConfig.Opt o, Object v) {
        if (o.kind == ChronoConfig.Opt.INT) return String.valueOf(((Number) v).intValue());
        return trimZeros(((Number) v).doubleValue());
    }

    private static String trimZeros(double d) {
        String s = String.format(Locale.ROOT, "%.3f", d);
        return s.replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    private boolean boolOf(ChronoConfig.Opt o) {
        Boolean p = pendingBool.get(o);
        return p != null ? p : (Boolean) o.value.get();
    }

    private String textOf(ChronoConfig.Opt o) {
        String p = pendingText.get(o);
        return p != null ? p : fmt(o, o.value.get());
    }

    private static ITextComponent onOff(boolean b) {
        return b ? new TranslationTextComponent("gui.chrono_bubble.on").withStyle(TextFormatting.GREEN)
                 : new TranslationTextComponent("gui.chrono_bubble.off").withStyle(TextFormatting.RED);
    }

    // ---------- layout ----------

    @Override
    protected void init() {
        // cabe em telas estreitas (escala de interface grande): rótulo encosta na esquerda e o controle na direita
        controlX = Math.min(this.width / 2 + 30, this.width - CONTROL_W - 10);
        labelX = Math.max(10, Math.min(this.width / 2 - 190, controlX - 220));

        List<ChronoConfig.Opt> list = pages.get(page);
        for (int i = 0; i < list.size(); i++) {
            final ChronoConfig.Opt o = list.get(i);
            int y = TOP + i * ROW_H;
            boolean canEdit = editable(o);
            if (o.kind == ChronoConfig.Opt.BOOL) {
                Button btn = this.addButton(new Button(controlX, y, CONTROL_W, 20, onOff(boolOf(o)), b -> {
                    pendingBool.put(o, !boolOf(o));
                    b.setMessage(onOff(boolOf(o)));
                }));
                btn.active = canEdit;
            } else {
                TextFieldWidget tf = new TextFieldWidget(this.font, controlX, y, CONTROL_W, 20, StringTextComponent.EMPTY);
                tf.setFilter(o.kind == ChronoConfig.Opt.INT ? s -> s.matches("[0-9-]*") : s -> s.matches("[0-9.,-]*"));
                tf.setValue(textOf(o));
                tf.setResponder(s -> pendingText.put(o, s));
                tf.setEditable(canEdit);
                this.addButton(tf);
            }
        }

        int by = this.height - 52;
        this.addButton(new Button(this.width / 2 - 155, by, 100, 20, new TranslationTextComponent("gui.chrono_bubble.prev"), b -> changePage(-1)));
        this.addButton(new Button(this.width / 2 + 55, by, 100, 20, new TranslationTextComponent("gui.chrono_bubble.next"), b -> changePage(1)));
        this.addButton(new Button(this.width / 2 - 155, this.height - 28, 100, 20, new TranslationTextComponent("gui.chrono_bubble.save"), b -> save()));
        Button reset = this.addButton(new Button(this.width / 2 - 50, this.height - 28, 100, 20, new TranslationTextComponent("gui.chrono_bubble.reset"), b -> resetPage()));
        reset.active = !list.isEmpty() && editable(list.get(0));
        this.addButton(new Button(this.width / 2 + 55, this.height - 28, 100, 20, new TranslationTextComponent("gui.chrono_bubble.cancel"), b -> this.minecraft.setScreen(parent)));
    }

    private void changePage(int delta) {
        page = (page + delta + pages.size()) % pages.size();
        status = StringTextComponent.EMPTY;
        this.init(this.minecraft, this.width, this.height);
    }

    private void resetPage() {
        for (ChronoConfig.Opt o : pages.get(page)) {
            if (!editable(o)) continue;
            if (o.kind == ChronoConfig.Opt.BOOL) pendingBool.put(o, (Boolean) o.def);
            else pendingText.put(o, fmt(o, o.def));
        }
        status = new TranslationTextComponent("gui.chrono_bubble.reset_done").withStyle(TextFormatting.GRAY);
        this.init(this.minecraft, this.width, this.height);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void save() {
        // valida tudo antes de gravar
        Map<ChronoConfig.Opt, Object> toSet = new HashMap<>();
        for (Map.Entry<ChronoConfig.Opt, Boolean> e : pendingBool.entrySet()) {
            if (editable(e.getKey())) toSet.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<ChronoConfig.Opt, String> e : pendingText.entrySet()) {
            ChronoConfig.Opt o = e.getKey();
            if (!editable(o)) continue;
            try {
                double d = Double.parseDouble(e.getValue().trim().replace(',', '.'));
                d = Math.max(o.min, Math.min(o.max, d));
                toSet.put(o, o.kind == ChronoConfig.Opt.INT ? (Object) Integer.valueOf((int) Math.round(d)) : (Object) Double.valueOf(d));
            } catch (NumberFormatException ex) {
                status = new TranslationTextComponent("gui.chrono_bubble.invalid", new TranslationTextComponent(o.labelKey()))
                        .withStyle(TextFormatting.RED);
                return;
            }
        }
        try {
            Set<ForgeConfigSpec> touched = new HashSet<>();
            for (Map.Entry<ChronoConfig.Opt, Object> e : toSet.entrySet()) {
                ((ForgeConfigSpec.ConfigValue) e.getKey().value).set(e.getValue());
                touched.add(e.getKey().spec());
            }
            for (ForgeConfigSpec spec : touched) spec.save();
        } catch (Throwable t) {
            status = new TranslationTextComponent("gui.chrono_bubble.save_failed", t.getClass().getSimpleName()).withStyle(TextFormatting.RED);
            return;
        }
        this.minecraft.setScreen(parent);
    }

    // ---------- desenho ----------

    private ITextComponent pageTitle(List<ChronoConfig.Opt> list) {
        ChronoConfig.Opt first = list.get(0);
        IFormattableTextComponent t = new TranslationTextComponent(first.sectionKey()).withStyle(TextFormatting.AQUA);
        String where = first.server ? "gui.chrono_bubble.scope_server" : "gui.chrono_bubble.scope_client";
        return t.append(new StringTextComponent(" ")).append(new TranslationTextComponent(where).withStyle(TextFormatting.DARK_AQUA))
                .append(new StringTextComponent(" (" + (page + 1) + "/" + pages.size() + ")").withStyle(TextFormatting.GRAY));
    }

    /** Aviso de por que a página está só para leitura (ou vazio se dá para editar). */
    private ITextComponent readOnlyNote(List<ChronoConfig.Opt> list) {
        if (list.isEmpty() || editable(list.get(0))) return StringTextComponent.EMPTY;
        String key = this.minecraft.level == null ? "gui.chrono_bubble.readonly_menu" : "gui.chrono_bubble.readonly_remote";
        return new TranslationTextComponent(key).withStyle(TextFormatting.YELLOW);
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partial) {
        this.renderBackground(ms);
        drawCenteredString(ms, this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        List<ChronoConfig.Opt> list = pages.get(page);
        drawCenteredString(ms, this.font, pageTitle(list), this.width / 2, 22, 0xFFFFFF);
        drawCenteredString(ms, this.font, readOnlyNote(list), this.width / 2, 36, 0xFFFFFF);

        int maxLabel = controlX - labelX - 6;
        for (int i = 0; i < list.size(); i++) {
            ChronoConfig.Opt o = list.get(i);
            String label = new TranslationTextComponent(o.labelKey()).getString();
            if (this.font.width(label) > maxLabel) label = this.font.plainSubstrByWidth(label, maxLabel - this.font.width("...")) + "...";
            drawString(ms, this.font, label, labelX, TOP + i * ROW_H + 6, editable(o) ? 0xE0E0E0 : 0x909090);
        }
        drawCenteredString(ms, this.font, status, this.width / 2, this.height - 66, 0xFFFFFF);
        super.render(ms, mouseX, mouseY, partial);

        // dica ao passar o mouse sobre o nome da opção
        for (int i = 0; i < list.size(); i++) {
            int y = TOP + i * ROW_H;
            if (mouseX >= labelX && mouseX < controlX - 2 && mouseY >= y && mouseY < y + 20) {
                ChronoConfig.Opt o = list.get(i);
                IFormattableTextComponent tip = new TranslationTextComponent(o.tooltipKey());
                if (o.kind != ChronoConfig.Opt.BOOL) {
                    tip.append(new StringTextComponent("  [" + trimZeros(o.min) + " – " + trimZeros(o.max) + "]").withStyle(TextFormatting.GRAY));
                }
                this.renderTooltip(ms, this.font.split(tip, 220), mouseX, mouseY);
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
