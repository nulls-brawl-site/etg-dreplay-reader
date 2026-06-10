package com.etgdreplay.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DReplayBoardView extends View {
    public interface StepListener {
        void onStepChanged(DReplayBoardView view);
    }

    private final DReplayParser.Replay replay;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final int greenTop = Color.rgb(27, 132, 91);
    private final int greenBottom = Color.rgb(14, 81, 66);
    private final int panelColor = Color.argb(210, 13, 42, 46);
    private final int panelStroke = Color.argb(80, 255, 255, 255);
    private final int cardWhite = Color.rgb(252, 250, 246);
    private final int cardShadow = Color.argb(70, 0, 0, 0);
    private final int cardBorder = Color.argb(80, 10, 23, 26);
    private final int cardRed = Color.rgb(213, 63, 70);
    private final int cardBlack = Color.rgb(31, 42, 54);
    private final int accent = Color.rgb(87, 190, 255);
    private int step;
    private BoardState state;
    private StepListener stepListener;
    private float downX;
    private float downY;

    public DReplayBoardView(Context context, DReplayParser.Replay replay) {
        super(context);
        this.replay = replay;
        this.step = 0;
        this.state = buildState(0);
        setWillNotDraw(false);
        setFocusable(true);
        textPaint.setSubpixelText(true);
    }

    public void setStepListener(StepListener listener) {
        this.stepListener = listener;
    }

    public int getStep() {
        return step;
    }

    public int getMaxStep() {
        return replay.actions.size();
    }

    public void setStep(int value) {
        int clamped = Math.max(0, Math.min(value, getMaxStep()));
        if (clamped == step && state != null) {
            return;
        }
        step = clamped;
        state = buildState(step);
        invalidate();
        if (stepListener != null) {
            stepListener.onStepChanged(this);
        }
    }

    public void next() {
        setStep(step + 1);
    }

    public void previous() {
        setStep(step - 1);
    }

    public String getStepText() {
        if (step <= 0 || replay.actions.isEmpty()) {
            return "Раздача · 0/" + getMaxStep();
        }
        DReplayParser.Action action = replay.actions.get(step - 1);
        return replay.formatActionTime(action) + " · " + replay.playerName(action.player) + ": " + replay.actionText(action) + " · " + step + "/" + getMaxStep();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        drawBackground(canvas, width, height);
        drawHeader(canvas, width);
        drawPlayers(canvas, width, height);
        drawTable(canvas, width, height);
        drawDeck(canvas, width, height);
        drawFooter(canvas, width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > dp(44) && Math.abs(dx) > Math.abs(dy)) {
                if (dx < 0) {
                    next();
                } else {
                    previous();
                }
            } else if (event.getX() > getWidth() * 0.52f) {
                next();
            } else {
                previous();
            }
            return true;
        }
        return true;
    }

    private void drawBackground(Canvas canvas, int width, int height) {
        paint.setShader(new LinearGradient(0, 0, 0, height, greenTop, greenBottom, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, width, height), dp(18), dp(18), paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(45, 255, 255, 255));
        for (int i = 0; i < 7; i++) {
            float inset = dp(18 + i * 18);
            rect.set(inset, inset, width - inset, height - inset);
            canvas.drawOval(rect, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHeader(Canvas canvas, int width) {
        float pad = dp(12);
        rect.set(pad, pad, width - pad, dp(64));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(panelColor);
        canvas.drawRoundRect(rect, dp(13), dp(13), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(panelStroke);
        canvas.drawRoundRect(rect, dp(13), dp(13), paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setFakeBoldText(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(15));
        canvas.drawText(replay.reportTitle(), dp(24), dp(35), textPaint);

        textPaint.setFakeBoldText(false);
        textPaint.setColor(Color.argb(210, 255, 255, 255));
        textPaint.setTextSize(dp(11));
        String meta = "Козырь " + replay.cardLabel(replay.trump) + " · Раунд " + state.roundNumber + " · Добор " + state.deckLeft;
        canvas.drawText(ellipsize(meta, textPaint, width - dp(48)), dp(24), dp(53), textPaint);
    }

    private void drawPlayers(Canvas canvas, int width, int height) {
        drawPlayerPanel(canvas, 1, dp(80), width, true);
        drawPlayerPanel(canvas, 0, height - dp(118), width, false);
    }

    private void drawPlayerPanel(Canvas canvas, int player, float y, int width, boolean top) {
        int count = player < state.hands.length ? state.hands[player].size() : 0;
        String name = replay.playerName(player);
        String label = name + " · " + count + " карт";
        float x = dp(18);
        float chipW = Math.min(width - dp(36), Math.max(dp(132), textWidth(label, dp(12)) + dp(26)));
        rect.set(x, y, x + chipW, y + dp(28));
        paint.setColor(Color.argb(top ? 160 : 185, 4, 26, 28));
        canvas.drawRoundRect(rect, dp(14), dp(14), paint);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(dp(12));
        textPaint.setColor(Color.WHITE);
        canvas.drawText(ellipsize(label, textPaint, chipW - dp(20)), x + dp(13), y + dp(19), textPaint);
        textPaint.setFakeBoldText(false);

        if (player >= state.hands.length) {
            return;
        }
        float cardW = cardWidth(width);
        float cardH = cardHeight(cardW);
        float handY = top ? y + dp(36) : y - cardH - dp(8);
        drawHand(canvas, state.hands[player], dp(16), handY, width - dp(32), cardW, cardH, top);
    }

    private void drawHand(Canvas canvas, List<String> hand, float x, float y, float available, float cardW, float cardH, boolean compact) {
        int count = hand.size();
        if (count == 0) {
            drawEmptyHand(canvas, x, y, available, cardH);
            return;
        }
        float overlap = count == 1 ? 0 : Math.min(cardW * 0.68f, (available - cardW) / (count - 1));
        if (overlap < dp(12)) {
            overlap = dp(12);
        }
        float total = cardW + overlap * (count - 1);
        float start = x + Math.max(0, (available - total) / 2f);
        for (int i = 0; i < count; i++) {
            DReplayParser.Card card = DReplayParser.parseCard(hand.get(i));
            float offsetY = compact ? (i % 2) * dp(2) : -((i % 3) * dp(2));
            drawCard(canvas, card, start + overlap * i, y + offsetY, cardW, cardH, 0f, false);
        }
    }

    private void drawEmptyHand(Canvas canvas, float x, float y, float available, float cardH) {
        rect.set(x + available / 2f - dp(46), y + cardH / 2f - dp(13), x + available / 2f + dp(46), y + cardH / 2f + dp(13));
        paint.setColor(Color.argb(70, 255, 255, 255));
        canvas.drawRoundRect(rect, dp(13), dp(13), paint);
        textPaint.setTextSize(dp(11));
        textPaint.setColor(Color.argb(180, 255, 255, 255));
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("пусто", x + available / 2f, rect.centerY() + dp(4), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawTable(Canvas canvas, int width, int height) {
        float cardW = cardWidth(width) * 1.08f;
        float cardH = cardHeight(cardW);
        float centerY = height * 0.50f;
        if (state.table.isEmpty()) {
            rect.set(dp(34), centerY - dp(35), width - dp(34), centerY + dp(35));
            paint.setColor(Color.argb(70, 0, 0, 0));
            canvas.drawRoundRect(rect, dp(16), dp(16), paint);
            textPaint.setColor(Color.argb(200, 255, 255, 255));
            textPaint.setTextSize(dp(13));
            textPaint.setFakeBoldText(true);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(step == 0 ? "начальная раздача" : "стол очищен", width / 2f, centerY + dp(5), textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setFakeBoldText(false);
            return;
        }
        float pairW = cardW * 1.52f;
        float gap = dp(10);
        int maxPerRow = Math.max(1, (int) ((width - dp(44) + gap) / (pairW + gap)));
        int rows = (int) Math.ceil(state.table.size() / (float) maxPerRow);
        float blockH = rows * (cardH + dp(12)) - dp(12);
        float startY = centerY - blockH / 2f;
        for (int i = 0; i < state.table.size(); i++) {
            int row = i / maxPerRow;
            int col = i % maxPerRow;
            int rowCount = Math.min(maxPerRow, state.table.size() - row * maxPerRow);
            float totalW = rowCount * pairW + (rowCount - 1) * gap;
            float startX = (width - totalW) / 2f;
            float x = startX + col * (pairW + gap);
            float y = startY + row * (cardH + dp(12));
            TablePair pair = state.table.get(i);
            drawCard(canvas, DReplayParser.parseCard(pair.attack), x, y, cardW, cardH, -4f, false);
            if (pair.beat != null && pair.beat.length() > 0) {
                drawCard(canvas, DReplayParser.parseCard(pair.beat), x + cardW * 0.48f, y + dp(8), cardW, cardH, 9f, false);
            }
        }
    }

    private void drawDeck(Canvas canvas, int width, int height) {
        float cardW = cardWidth(width) * 0.92f;
        float cardH = cardHeight(cardW);
        float x = width - cardW - dp(22);
        float y = height * 0.50f - cardH / 2f;
        if (state.deckLeft > 0) {
            for (int i = 0; i < Math.min(3, state.deckLeft); i++) {
                drawCardBack(canvas, x - i * dp(3), y - i * dp(2), cardW, cardH);
            }
            textPaint.setTextSize(dp(12));
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(state.deckLeft), x + cardW / 2f, y + cardH + dp(18), textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setFakeBoldText(false);
        }
        if (replay.trump != null) {
            drawCard(canvas, replay.trump, dp(22), y + dp(10), cardW, cardH, -10f, true);
        }
    }

    private void drawFooter(Canvas canvas, int width, int height) {
        float pad = dp(12);
        float top = height - dp(50);
        rect.set(pad, top, width - pad, height - pad);
        paint.setColor(panelColor);
        canvas.drawRoundRect(rect, dp(13), dp(13), paint);
        float progress = getMaxStep() == 0 ? 0f : step / (float) getMaxStep();
        rect.set(pad + dp(10), top + dp(8), width - pad - dp(10), top + dp(12));
        paint.setColor(Color.argb(70, 255, 255, 255));
        canvas.drawRoundRect(rect, dp(2), dp(2), paint);
        rect.right = rect.left + rect.width() * progress;
        paint.setColor(accent);
        canvas.drawRoundRect(rect, dp(2), dp(2), paint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(12));
        textPaint.setFakeBoldText(false);
        canvas.drawText(ellipsize(getStepText(), textPaint, width - dp(44)), pad + dp(10), top + dp(31), textPaint);
    }

    private void drawCard(Canvas canvas, DReplayParser.Card card, float x, float y, float w, float h, float rotate, boolean trump) {
        if (card == null) {
            return;
        }
        canvas.save();
        if (rotate != 0f) {
            canvas.rotate(rotate, x + w / 2f, y + h / 2f);
        }
        rect.set(x + dp(2), y + dp(3), x + w + dp(2), y + h + dp(3));
        paint.setColor(cardShadow);
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        rect.set(x, y, x + w, y + h);
        paint.setColor(cardWhite);
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(trump ? dp(2) : dp(1));
        paint.setColor(trump || (replay.trump != null && card.suit == replay.trump.suit) ? Color.rgb(238, 189, 68) : cardBorder);
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.FILL);

        int color = card.isRed() ? cardRed : cardBlack;
        textPaint.setColor(color);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(w * 0.28f);
        canvas.drawText(card.rankLabel(), x + w * 0.14f, y + h * 0.30f, textPaint);
        textPaint.setTextSize(w * 0.34f);
        canvas.drawText(card.suitGlyph(), x + w * 0.14f, y + h * 0.58f, textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(w * 0.22f);
        canvas.drawText(card.rankLabel(), x + w * 0.86f, y + h * 0.84f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setFakeBoldText(false);
        canvas.restore();
    }

    private void drawCardBack(Canvas canvas, float x, float y, float w, float h) {
        rect.set(x + dp(2), y + dp(3), x + w + dp(2), y + h + dp(3));
        paint.setColor(cardShadow);
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        rect.set(x, y, x + w, y + h);
        paint.setColor(Color.rgb(42, 122, 203));
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(170, 255, 255, 255));
        rect.inset(dp(7), dp(7));
        canvas.drawRoundRect(rect, dp(5), dp(5), paint);
        paint.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(x + w * 0.50f, y + h * 0.22f);
        path.lineTo(x + w * 0.70f, y + h * 0.50f);
        path.lineTo(x + w * 0.50f, y + h * 0.78f);
        path.lineTo(x + w * 0.30f, y + h * 0.50f);
        path.close();
        paint.setColor(Color.argb(130, 255, 255, 255));
        canvas.drawPath(path, paint);
    }

    private BoardState buildState(int untilStep) {
        BoardState board = new BoardState();
        int playerCount = Math.max(2, replay.players.size());
        board.hands = createHands(playerCount);
        int handSize = 6;
        int index = 0;
        for (int player = 0; player < playerCount; player++) {
            for (int c = 0; c < handSize && index < replay.deck.size(); c++, index++) {
                board.hands[player].add(replay.deck.get(index).raw);
            }
        }
        board.drawIndex = index;
        board.roundNumber = 1;
        for (int i = 0; i < untilStep && i < replay.actions.size(); i++) {
            apply(board, replay.actions.get(i));
        }
        board.deckLeft = Math.max(0, replay.deck.size() - board.drawIndex);
        return board;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String>[] createHands(int count) {
        ArrayList<String>[] hands = new ArrayList[count];
        for (int i = 0; i < count; i++) {
            hands[i] = new ArrayList<String>();
        }
        return hands;
    }

    private void apply(BoardState board, DReplayParser.Action action) {
        int player = action.player >= 0 && action.player < board.hands.length ? action.player : 0;
        if (action.code == DReplayParser.ACTION_ATTACK || action.code == DReplayParser.ACTION_TRANSFER) {
            removeCard(board.hands[player], action.arg1);
            if (action.arg1 != null && action.arg1.length() > 0) {
                board.table.add(new TablePair(action.arg1));
            }
        } else if (action.code == DReplayParser.ACTION_BEAT) {
            removeCard(board.hands[player], action.arg2);
            TablePair pair = findPair(board.table, action.arg1);
            if (pair == null) {
                pair = new TablePair(action.arg1);
                board.table.add(pair);
            }
            pair.beat = action.arg2;
        } else if (action.code == DReplayParser.ACTION_TAKE) {
            for (TablePair pair : board.table) {
                if (pair.attack != null && pair.attack.length() > 0) {
                    board.hands[player].add(pair.attack);
                }
                if (pair.beat != null && pair.beat.length() > 0) {
                    board.hands[player].add(pair.beat);
                }
            }
            board.table.clear();
            drawToSix(board);
            board.roundNumber++;
        } else if (action.code == DReplayParser.ACTION_DONE) {
            board.table.clear();
            drawToSix(board);
            board.roundNumber++;
        }
    }

    private TablePair findPair(List<TablePair> table, String attack) {
        for (TablePair pair : table) {
            if (safeEquals(pair.attack, attack)) {
                return pair;
            }
        }
        return null;
    }

    private boolean removeCard(List<String> cards, String raw) {
        if (raw == null) {
            return false;
        }
        for (int i = 0; i < cards.size(); i++) {
            if (safeEquals(cards.get(i), raw)) {
                cards.remove(i);
                return true;
            }
        }
        return false;
    }

    private boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private void drawToSix(BoardState board) {
        for (int player = 0; player < board.hands.length; player++) {
            while (board.hands[player].size() < 6 && board.drawIndex < replay.deck.size()) {
                board.hands[player].add(replay.deck.get(board.drawIndex).raw);
                board.drawIndex++;
            }
        }
    }

    private float cardWidth(int width) {
        return Math.max(dp(38), Math.min(dp(54), width / 8.5f));
    }

    private float cardHeight(float cardW) {
        return cardW * 1.42f;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float textWidth(String text, float sp) {
        textPaint.setTextSize(sp);
        return textPaint.measureText(text == null ? "" : text);
    }

    private String ellipsize(String value, Paint p, float maxWidth) {
        if (value == null) {
            return "";
        }
        if (p.measureText(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && p.measureText(value.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return value.substring(0, Math.max(0, end)) + ellipsis;
    }

    private static final class BoardState {
        ArrayList<String>[] hands;
        final List<TablePair> table = new ArrayList<TablePair>();
        int drawIndex;
        int deckLeft;
        int roundNumber;
    }

    private static final class TablePair {
        final String attack;
        String beat;

        TablePair(String attack) {
            this.attack = attack;
        }
    }
}
