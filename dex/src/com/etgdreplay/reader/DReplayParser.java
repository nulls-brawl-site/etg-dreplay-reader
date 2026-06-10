package com.etgdreplay.reader;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DReplayParser {
    public static final int ACTION_ATTACK = 5;
    public static final int ACTION_BEAT = 6;
    public static final int ACTION_TRANSFER = 7;
    public static final int ACTION_TAKE = 8;
    public static final int ACTION_DONE = 9;

    private DReplayParser() {
    }

    public static Replay parse(String raw) {
        Replay replay = new Replay(raw == null ? "" : raw);
        String[] lines = replay.raw.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.length() == 0) {
                continue;
            }
            String[] parts = trimmed.split("\\|", -1);
            if (parts.length == 0) {
                continue;
            }
            if ("H".equals(parts[0])) {
                parseHeader(replay, parts);
            } else if ("D".equals(parts[0])) {
                parseDeck(replay, parts);
            } else if ("A".equals(parts[0])) {
                parseAction(replay, parts);
            } else {
                replay.unknownLines.add(trimmed);
            }
        }
        replay.buildRounds();
        return replay;
    }

    private static void parseHeader(Replay replay, String[] parts) {
        if (parts.length > 1) {
            replay.timestampSeconds = safeLong(parts[1], 0L);
        }
        int trumpSuit = parts.length > 2 ? safeInt(parts[2], -1) : -1;
        int trumpRank = parts.length > 3 ? safeInt(parts[3], -1) : -1;
        if (trumpSuit >= 0 && trumpRank >= 0) {
            replay.trump = new Card(trumpSuit, trumpRank, 0, trumpSuit + "_" + trumpRank + "_0");
        }
        if (parts.length > 4) {
            replay.firstAttacker = safeInt(parts[4], -1);
        }
        if (parts.length > 6) {
            for (int i = 5; i < parts.length - 1; i++) {
                replay.players.add(parsePlayer(i - 5, parts[i]));
            }
            replay.loser = safeInt(parts[parts.length - 1], -1);
        }
    }

    private static Player parsePlayer(int index, String raw) {
        String id = "";
        String name = raw == null ? "" : raw;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            id = name.substring(0, colon);
            name = name.substring(colon + 1);
        }
        if (name.trim().length() == 0) {
            name = "Игрок " + index;
        }
        return new Player(index, id, name.trim());
    }

    private static void parseDeck(Replay replay, String[] parts) {
        if (parts.length < 2 || parts[1].trim().length() == 0) {
            return;
        }
        String[] tokens = parts[1].split(",", -1);
        for (String token : tokens) {
            Card card = parseCard(token);
            if (card != null) {
                replay.deck.add(card);
            }
        }
    }

    private static void parseAction(Replay replay, String[] parts) {
        if (parts.length < 4) {
            return;
        }
        Action action = new Action();
        action.timeMs = safeLong(parts[1], 0L);
        action.player = safeInt(parts[2], -1);
        action.code = safeInt(parts[3], -1);
        action.arg1 = parts.length > 4 ? parts[4] : "";
        action.arg2 = parts.length > 5 ? parts[5] : "";
        replay.actions.add(action);
    }

    public static Card parseCard(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.length() == 0) {
            return null;
        }
        String[] parts = trimmed.split("_", -1);
        if (parts.length < 2) {
            return new Card(-1, -1, 0, trimmed);
        }
        int suit = safeInt(parts[0], -1);
        int rank = safeInt(parts[1], -1);
        int extra = parts.length > 2 ? safeInt(parts[2], 0) : 0;
        return new Card(suit, rank, extra, trimmed);
    }

    private static int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long safeLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static final class Replay {
        public final String raw;
        public long timestampSeconds;
        public Card trump;
        public int firstAttacker = -1;
        public int loser = -1;
        public final List<Player> players = new ArrayList<Player>();
        public final List<Card> deck = new ArrayList<Card>();
        public final List<Action> actions = new ArrayList<Action>();
        public final List<List<Action>> rounds = new ArrayList<List<Action>>();
        public final List<String> unknownLines = new ArrayList<String>();

        Replay(String raw) {
            this.raw = raw;
        }

        void buildRounds() {
            rounds.clear();
            List<Action> current = new ArrayList<Action>();
            for (Action action : actions) {
                current.add(action);
                if (action.code == ACTION_TAKE || action.code == ACTION_DONE) {
                    rounds.add(current);
                    current = new ArrayList<Action>();
                }
            }
            if (!current.isEmpty()) {
                rounds.add(current);
            }
        }

        public String reportTitle() {
            if (players.size() >= 2) {
                return players.get(0).name + " vs " + players.get(1).name;
            }
            return "DReplay";
        }

        public String shortSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Дурак");
            if (hasTransfer()) {
                sb.append(", переводной");
            }
            sb.append(" · ").append(players.size()).append(" игрока");
            sb.append(" · ").append(actions.size()).append(" действий");
            if (loser >= 0 && loser < players.size()) {
                sb.append(" · проиграл ").append(players.get(loser).name);
            }
            return sb.toString();
        }

        public String buildReport() {
            StringBuilder sb = new StringBuilder(4096);
            appendLine(sb, "DReplay Reader");
            appendLine(sb, "");
            appendLine(sb, "Итог");
            appendLine(sb, "Игра: " + (hasTransfer() ? "дурак переводной" : "дурак"));
            appendLine(sb, "Дата: " + formatDate(timestampSeconds));
            appendLine(sb, "Козырь: " + cardLabel(trump));
            appendLine(sb, "Первый ход: " + playerName(firstAttacker));
            if (loser >= 0) {
                appendLine(sb, "Проиграл: " + playerName(loser));
                int winner = winnerIndex();
                if (winner >= 0) {
                    appendLine(sb, "Победил: " + playerName(winner));
                }
            }
            appendLine(sb, "");
            appendLine(sb, "Игроки");
            for (Player player : players) {
                String id = player.id.length() == 0 ? "" : " · id " + player.id;
                appendLine(sb, player.index + ": " + player.name + id);
            }
            appendLine(sb, "");
            appendLine(sb, "Колода");
            appendLine(sb, "Карт: " + deck.size());
            appendInitialHands(sb);
            appendLine(sb, "Добор: " + drawPileSize() + " карт");
            if (!deck.isEmpty()) {
                appendLine(sb, "Нижняя карта: " + cardLabel(deck.get(deck.size() - 1)));
            }
            appendLine(sb, "");
            appendLine(sb, "Коды");
            appendLine(sb, "5 атака/подкидывание, 6 бьёт, 7 перевод, 8 взял, 9 бито.");
            appendLine(sb, "");
            appendLine(sb, "Ходы");
            if (rounds.isEmpty()) {
                appendLine(sb, "Действий нет.");
            } else {
                for (int i = 0; i < rounds.size(); i++) {
                    appendLine(sb, "#" + (i + 1));
                    List<Action> round = rounds.get(i);
                    for (Action action : round) {
                        appendLine(sb, formatTime(action.timeMs) + "  " + playerName(action.player) + ": " + actionText(action));
                    }
                    appendLine(sb, "");
                }
            }
            if (!unknownLines.isEmpty()) {
                appendLine(sb, "Неизвестные строки");
                for (String line : unknownLines) {
                    appendLine(sb, line);
                }
            }
            return sb.toString();
        }

        private void appendInitialHands(StringBuilder sb) {
            int count = players.size() == 0 ? 2 : players.size();
            int handSize = 6;
            for (int player = 0; player < count; player++) {
                int from = player * handSize;
                int to = Math.min(from + handSize, deck.size());
                if (from >= deck.size()) {
                    break;
                }
                StringBuilder hand = new StringBuilder();
                for (int i = from; i < to; i++) {
                    if (hand.length() > 0) {
                        hand.append(", ");
                    }
                    hand.append(cardLabel(deck.get(i)));
                }
                appendLine(sb, "Рука " + playerName(player) + ": " + hand);
            }
        }

        private int drawPileSize() {
            int handCards = Math.max(players.size(), 2) * 6;
            return Math.max(0, deck.size() - handCards);
        }

        public boolean hasTransfer() {
            for (Action action : actions) {
                if (action.code == ACTION_TRANSFER) {
                    return true;
                }
            }
            return false;
        }

        private int winnerIndex() {
            if (players.size() == 2 && loser >= 0 && loser < players.size()) {
                return loser == 0 ? 1 : 0;
            }
            return -1;
        }

        public String actionText(Action action) {
            if (action.code == ACTION_ATTACK) {
                return "ходит/подкидывает " + cardLabel(parseCard(action.arg1));
            }
            if (action.code == ACTION_BEAT) {
                return "бьёт " + cardLabel(parseCard(action.arg1)) + " картой " + cardLabel(parseCard(action.arg2));
            }
            if (action.code == ACTION_TRANSFER) {
                return "переводит " + cardLabel(parseCard(action.arg1));
            }
            if (action.code == ACTION_TAKE) {
                return "берёт";
            }
            if (action.code == ACTION_DONE) {
                return "бито";
            }
            String args = (action.arg1 == null ? "" : action.arg1) + (action.arg2 == null || action.arg2.length() == 0 ? "" : " | " + action.arg2);
            return "код " + action.code + (args.length() == 0 ? "" : " · " + args);
        }

        public String playerName(int index) {
            if (index >= 0 && index < players.size()) {
                return players.get(index).name;
            }
            return "Игрок " + index;
        }

        public String cardLabel(Card card) {
            if (card == null) {
                return "-";
            }
            return card.label(trump == null ? -1 : trump.suit);
        }

        public String formatActionTime(Action action) {
            return action == null ? "00:00.000" : formatTime(action.timeMs);
        }
    }

    public static final class Player {
        public final int index;
        public final String id;
        public final String name;

        Player(int index, String id, String name) {
            this.index = index;
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
        }
    }

    public static final class Card {
        public final int suit;
        public final int rank;
        public final int extra;
        public final String raw;

        Card(int suit, int rank, int extra, String raw) {
            this.suit = suit;
            this.rank = rank;
            this.extra = extra;
            this.raw = raw == null ? "" : raw;
        }

        public String label(int trumpSuit) {
            if (rank == 15) {
                return "Joker" + (extra > 0 ? extra : "") + " (" + raw + ")";
            }
            String rankText;
            if (rank == 11) {
                rankText = "J";
            } else if (rank == 12) {
                rankText = "Q";
            } else if (rank == 13) {
                rankText = "K";
            } else if (rank == 14) {
                rankText = "A";
            } else if (rank > 0) {
                rankText = String.format(Locale.US, "%d", rank);
            } else {
                rankText = "?";
            }
            String mark = suit == trumpSuit ? "*" : "";
            return "S" + suit + ":" + rankText + mark + " (" + raw + ")";
        }

        public String rankLabel() {
            if (rank == 11) {
                return "J";
            }
            if (rank == 12) {
                return "Q";
            }
            if (rank == 13) {
                return "K";
            }
            if (rank == 14) {
                return "A";
            }
            if (rank == 15) {
                return "JK";
            }
            if (rank > 0) {
                return String.format(Locale.US, "%d", rank);
            }
            return "?";
        }

        public String suitGlyph() {
            if (rank == 15) {
                return extra == 2 ? "★" : "☆";
            }
            if (suit == 0) {
                return "♣";
            }
            if (suit == 1) {
                return "♦";
            }
            if (suit == 2) {
                return "♥";
            }
            if (suit == 3) {
                return "♠";
            }
            return "•";
        }

        public boolean isRed() {
            return rank == 15 || suit == 1 || suit == 2;
        }
    }

    public static final class Action {
        public long timeMs;
        public int player;
        public int code;
        public String arg1 = "";
        public String arg2 = "";
    }

    private static void appendLine(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }

    private static String formatDate(long seconds) {
        if (seconds <= 0) {
            return "-";
        }
        try {
            return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date(seconds * 1000L));
        } catch (Exception ignored) {
            return Long.toString(seconds);
        }
    }

    private static String formatTime(long ms) {
        long totalSeconds = Math.max(0L, ms) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long millis = Math.max(0L, ms) % 1000L;
        return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis);
    }
}
