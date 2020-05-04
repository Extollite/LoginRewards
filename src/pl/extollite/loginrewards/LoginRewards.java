package pl.extollite.loginrewards;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.service.RegisteredServiceProvider;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import cn.nukkit.utils.TextFormat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class LoginRewards extends PluginBase {
    private static final String format = "yyyy-MM-dd HH:mm:ss Z";

    private static LoginRewards instance;
    private Map<Integer, Map.Entry<String, List<String>>> rewards = new HashMap<>();
    private Map<UUID, Map.Entry<Integer, Date>> currentDay = new HashMap<>();
    private String prefix;
    private String sameDayMessage;

    static LoginRewards getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        instance = this;
        List<String> authors = this.getDescription().getAuthors();
        this.getLogger().info(TextFormat.GREEN + authors.get(0));
        parseConfig();
        loadPlayers();
        Item item = Item.get(10);
    }

    private void parseConfig() {
        Config config = this.getConfig();
        prefix = config.getString("prefix");
        sameDayMessage = config.getString("sameDayMessage");
        ConfigSection section = config.getSection("days");
        for (String key : section.getKeys(false)) {
            ConfigSection day = section.getSection(key);
            rewards.put(Integer.parseInt(key.replace("d", "")), new AbstractMap.SimpleImmutableEntry<>(day.getString("message"), day.getStringList("commands")));
        }
    }

    private void loadPlayers() {
        Config players = new Config(this.getDataFolder() + "/players.yml", Config.YAML);
        for (String key : players.getKeys(false)) {
            ConfigSection section = players.getSection(key);
            try {
                currentDay.put(UUID.fromString(key),
                        new AbstractMap.SimpleImmutableEntry<>(section.getInt("dayNr"), new SimpleDateFormat(format).parse(section.getString("lastClaimed"))));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().toLowerCase().equals("login") && !cmd.getName().toLowerCase().equals("reset_login")) {
            return true;
        }
        if (cmd.getName().toLowerCase().equals("login")) {
            Player p = (Player)sender;
            if(currentDay.containsKey(p.getUniqueId())){
                Calendar cal1 = Calendar.getInstance();
                Calendar cal2 = Calendar.getInstance();
                Date now = new Date(System.currentTimeMillis());
                Date lastUsed = currentDay.get(p.getUniqueId()).getValue();
                cal1.setTime(now);
                cal2.setTime(lastUsed);
                boolean sameDay = cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
                if(!sameDay){
                    Integer day = currentDay.get(p.getUniqueId()).getKey();
                    day += 1;
                    Map.Entry<String, List<String>> reward = rewards.get(day);
                    Config players = new Config(this.getDataFolder() + "/players.yml", Config.YAML);
                    ConfigSection section = players.getSection(p.getUniqueId().toString());
                    section.set("dayNr", day);
                    section.set("lastClaimed", new SimpleDateFormat(format).format(now));
                    players.set(p.getUniqueId().toString(), section);
                    players.save();
                    currentDay.replace(p.getUniqueId(),
                            new AbstractMap.SimpleImmutableEntry<>(day, now));
                    for(String command : reward.getValue()){
                        this.getServer().dispatchCommand(this.getServer().getConsoleSender(), command.replace("%player_name%", p.getName()));
                    }
                    p.sendMessage(prefix+reward.getKey());
                }
                else{
                    p.sendMessage(prefix+sameDayMessage);
                    return true;
                }
            }
            else{
                Integer day = 1;
                Map.Entry<String, List<String>> reward = rewards.get(day);
                Config players = new Config(this.getDataFolder() + "/players.yml", Config.YAML);
                Date now = new Date(System.currentTimeMillis());
                ConfigSection section = new ConfigSection();
                section.set("dayNr", day);
                section.set("lastClaimed", new SimpleDateFormat(format).format(now));
                players.set(p.getUniqueId().toString(), section);
                players.save();
                currentDay.put(p.getUniqueId(),
                        new AbstractMap.SimpleImmutableEntry<>(day, now));
                for(String command : reward.getValue()){
                    this.getServer().dispatchCommand(this.getServer().getConsoleSender(), command.replace("%player_name%", p.getName()));
                }
                p.sendMessage(prefix+reward.getKey());
            }
        } else if (cmd.getName().toLowerCase().equals("reset_login")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (!p.isOp() && !p.hasPermission("loginrewardsreset.command"))
                    return true;
            }
            currentDay.clear();
            Date now = new Date(System.currentTimeMillis());
            now.setTime(now.getTime()-86400000);
            Config players = new Config(this.getDataFolder() + "/players.yml", Config.YAML);
            for (String key : players.getKeys(false)) {
                players.remove(key);
            }
            ConfigSection section = new ConfigSection();
            section.set("dayNr", 0);
            section.set("lastClaimed", new SimpleDateFormat(format).format(now));
            for (Player player : this.getServer().getOnlinePlayers().values()) {
                currentDay.put(player.getUniqueId(),
                        new AbstractMap.SimpleImmutableEntry<>(0, now));
                players.set(player.getUniqueId().toString(), section);
            }
            players.save();
            sender.sendMessage(prefix+"Login rewards days reset!");
        }
        return true;
    }
}
