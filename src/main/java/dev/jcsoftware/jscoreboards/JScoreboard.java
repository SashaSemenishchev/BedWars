package dev.jcsoftware.jscoreboards;

import dev.jcsoftware.jscoreboards.exception.DuplicateTeamCreatedException;
import dev.jcsoftware.jscoreboards.exception.JScoreboardException;
import dev.jcsoftware.jscoreboards.exception.ScoreboardLineTooLongException;
import dev.jcsoftware.jscoreboards.exception.ScoreboardTeamNameTooLongException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

public class JScoreboard {

    private JScoreboardOptions options;

    private final List<JScoreboardTeam> teams = new ArrayList<>();
    private List<String> lines = new ArrayList<>();
    protected List<UUID> activePlayers = new ArrayList<>();

    private Scoreboard scoreboard;

    public JScoreboard(JScoreboardOptions options) {
        this.options = options;
    }

    public JScoreboard() {
        this.options = JScoreboardOptions.defaultOptions;
    }

    private void createBukkitScoreboardIfNull() {
        if (this instanceof JPerPlayerScoreboard) return;

        if (this.scoreboard == null) {
            ScoreboardManager scoreboardManager = Bukkit.getServer().getScoreboardManager();

            scoreboard = scoreboardManager.getNewScoreboard();

            for (UUID playerUUID : activePlayers) {
                Player player = Bukkit.getPlayer(playerUUID);

                if (player != null) {
                    player.setScoreboard(scoreboard);
                }
            }
        }
    }

    protected void updateScoreboard(boolean updateObjective) throws JScoreboardException {
        createBukkitScoreboardIfNull();
        updateScoreboard(scoreboard, lines, updateObjective);
    }

    private final Map<Scoreboard, List<String>> previousLinesMap = new HashMap<>();

    /**
     * Update the scoreboard for all players it is shown to.
     * @throws JScoreboardException If a String within the lines array is over 64 characters, this exception is thrown.
     */
    protected void updateScoreboard(Scoreboard scoreboard, List<String> lines, boolean updateObjective) throws JScoreboardException {
        boolean update = false;
        if (previousLinesMap.containsKey(scoreboard)) {
            if (previousLinesMap.get(scoreboard).equals(lines)) { // Are the lines the same? Don't take up server resources to change absolutely nothing
                updateTeams(scoreboard); // Update the teams anyway
                return;
            }

            // Size difference means unregister objective to reset and re-register teams correctly

            if (previousLinesMap.get(scoreboard).size() != lines.size()) {
                scoreboard.clearSlot(DisplaySlot.SIDEBAR);
                scoreboard.getEntries().forEach(scoreboard::resetScores);
                update = true;
                scoreboard.getTeams().forEach(team -> {
                    if (team.getName().contains("line")) {
                        team.unregister();
                    }
                });
            }
        }

        previousLinesMap.put(scoreboard, lines);

        Objective objective;

        if (scoreboard.getObjective("dummy") == null) {
            objective = scoreboard.registerNewObjective("dummy", "dummy", "dummy");
        } else {
            objective = scoreboard.getObjective("dummy");
        }
        //System.out.println("Update scoreboard: " + updateObjective);
        if(updateObjective || update){
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.setDisplayName(color(options.getScoreboardTitle()));
        }

        Objective healthObjective = scoreboard.getObjective("tabHealth");
        if (options.getTabHealthStyle() != JScoreboardTabHealthStyle.NONE) {
            if (healthObjective == null) {
                healthObjective = scoreboard.registerNewObjective(
                        "tabHealth",
                        "health",
                        "health",
                        options.getTabHealthStyle() == JScoreboardTabHealthStyle.HEARTS ? RenderType.HEARTS : RenderType.INTEGER
                );
            }

            healthObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        } else {
            if (healthObjective != null) {
                healthObjective.unregister();
            }
        }

        if (options.shouldShowHealthUnderName()) {
            healthObjective = scoreboard.getObjective("nameHealth");
            if (healthObjective == null) {
                healthObjective = scoreboard.registerNewObjective(
                        "nameHealth",
                        "health",
                        ChatColor.translateAlternateColorCodes('&', "&c❤")
                );
            }

            healthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        } else {
            healthObjective = scoreboard.getObjective("nameHealth");
            if (healthObjective != null) {
                healthObjective.unregister();
            }
        }

        List<String> colorCodeOptions = colorOptions(lines);

        int score = 1;

        for (String entry : lines) {
            if (entry.length() > 64) {
                throw new ScoreboardLineTooLongException();
            }

            Team team = scoreboard.getTeam("line" + score);

            if (team != null) {
                team.setPrefix(color(entry));
                team.getEntries().forEach(team::removeEntry);
                team.addEntry(colorCodeOptions.get(score));
            } else {
                team = scoreboard.registerNewTeam("line" + score);
                team.addEntry(colorCodeOptions.get(score));
                team.setPrefix(color(entry));
                objective.getScore(colorCodeOptions.get(score)).setScore(score);
            }

            score += 1;
        }

        updateTeams(scoreboard);
    }

    private void updateTeams(Scoreboard scoreboard) {
        this.teams.forEach(team -> team.refresh(scoreboard));
    }

    private List<String> colorOptions(List<String> lines) {
        List<String> colorCodeOptions = new ArrayList<>();
        for (ChatColor color : ChatColor.values()) {
            if (color.isFormat()) {
                continue;
            }

            for (ChatColor secondColor : ChatColor.values()) {
                if (secondColor.isFormat()) {
                    continue;
                }

                String option = color + " " + secondColor;

                if (color != secondColor && !colorCodeOptions.contains(option)) {
                    colorCodeOptions.add(option);

                    if (colorCodeOptions.size() == lines.size()) break;
                }
            }
        }

        return colorCodeOptions;
    }

    public void setLines(boolean upd, List<String> lines) throws JScoreboardException {
        Collections.reverse(lines);
        this.lines = lines;
        updateScoreboard(upd);
    }

    public void setLines(List<String> lines) throws JScoreboardException {
        this.setLines(false, lines);
    }

    /**
     * Add a player to the scoreboard
     * @param player The player to add
     */
    public void addPlayer(Player player) {
        this.activePlayers.add(player.getUniqueId());

        createBukkitScoreboardIfNull();
        player.setScoreboard(scoreboard);
        try {
            updateScoreboard(true);
        } catch (JScoreboardException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove the player from the JScoreboard. This will reset their scoreboard to the main scoreboard
     * @param player The player to remove
     */
    public void removePlayer(Player player) {
        this.activePlayers.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        teams.forEach(team -> {
            if (team.isOnTeam(player.getUniqueId())) {
                team.removePlayer(player);
            }
        });
        try {
            updateScoreboard(true);
        } catch (JScoreboardException e) {
            e.printStackTrace();
        }
    }

    /**
     * Find a team using a name
     * @param name The name to search for. Color codes will be stripped from both the team name and this variable.
     * @return The JScoreboardPlayerTeam found, if any. Will return null if no team exists
     */
    public Optional<JScoreboardTeam> findTeam(String name) {
        return teams.stream().filter(team -> ChatColor.stripColor(team.getName()).equalsIgnoreCase(ChatColor.stripColor(name)))
                .findAny();
    }

    /**
     * Create a team on the scoreboard. ChatColor.WHITE is used as the color for the team.
     * @param name The name for the new team. This name cannot be longer than 16 characters
     * @return The created JScoreboardPlayerTeam
     * @throws DuplicateTeamCreatedException If a team with that name already exists
     */
    public JScoreboardTeam createTeam(String name, String displayName) throws JScoreboardException {
        return createTeam(name, displayName, ChatColor.WHITE);
    }

    /**
     * Create a team on the scoreboard.
     * @param name The name for the new team. This name cannot be longer than 16 characters
     * @return The created JScoreboardPlayerTeam
     * @throws DuplicateTeamCreatedException If a team with that name already exists
     */
    public JScoreboardTeam createTeam(String name, String displayName, ChatColor teamColor) throws JScoreboardException {
        for (JScoreboardTeam team : this.teams) {
            if (ChatColor.stripColor(team.getName()).equalsIgnoreCase(ChatColor.stripColor(name))) {
                throw new DuplicateTeamCreatedException(name);
            }
        }

        if (name.length() > 16) {
            throw new ScoreboardTeamNameTooLongException(name);
        }

        createBukkitScoreboardIfNull();

        JScoreboardTeam team = new JScoreboardTeam(name, displayName, teamColor,this);
        team.refresh();
        this.teams.add(team);
        return team;
    }

    /**
     * Remove a team from the scoreboard
     * @param team The team to remove from the scoreboard
     */
    public void removeTeam(JScoreboardTeam team) {
        if (team.getScoreboard() != this) return;

        team.destroy();
        this.teams.remove(team);
    }

    /**
     * Destroy the scoreboard. This will reset all players to the server's main scoreboard
     * You should call this method inside of your JavaPlugin's onDisable method.
     */
    public void destroy() {
        for (UUID playerUUID : activePlayers) {
            Player player = Bukkit.getPlayer(playerUUID);

            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }

        for (JScoreboardTeam team : teams) {
            team.destroy();
        }

        this.activePlayers.clear();
        this.lines.clear();
        this.teams.clear();
        this.scoreboard = null;
    }

    public Scoreboard toBukkitScoreboard() {
        return scoreboard;
    }

    public JScoreboardOptions getOptions() {
        return options;
    }

    public void setOptions(JScoreboardOptions options) {
        this.options = options;
    }

    private String color(String string) {
        return ChatColor.translateAlternateColorCodes('&', string);
    }

    public List<JScoreboardTeam> getTeams() {
        return teams;
    }

}
