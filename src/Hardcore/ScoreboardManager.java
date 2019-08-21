package Hardcore;

import java.util.HashSet;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Team;
import net.evmodder.EvLib.EvUtils;

public class ScoreboardManager implements Listener{
	final HashSet<String> included;
	final int ADV_WL_TRIGGER, LVL_WL_TRIGGER;
	//final Scoreboard emptyBoard;
	final HCTweaks pl;

	public ScoreboardManager(HCTweaks plugin){
		pl = plugin;
		included = new HashSet<String>();
		included.addAll(pl.getConfig().getStringList("advancements-included"));
		ADV_WL_TRIGGER = pl.getConfig().getInt("set-whitelist-mode-when-has-x-advancements", 15);
		LVL_WL_TRIGGER = pl.getConfig().getInt("set-whitelist-mode-when-has-x-levels", 50);
		//emptyBoard = pl.getServer().getScoreboardManager().getNewScoreboard();
	}

	boolean isMainAdvancement(Advancement adv){
		int i = adv.getKey().getKey().indexOf('/');
		return adv.getKey().getNamespace().equals(NamespacedKey.MINECRAFT) && i != -1
				&& included.contains(adv.getKey().getKey().substring(0, i));
	}

	static String getAdvancementTeamName(int numAdvancements){
		StringBuilder builder = new StringBuilder("Adv_");
		int i = 0;
		while(i<numAdvancements/26){builder.append('a'); ++i;}
		char ch = 'z'; ch -= numAdvancements%26;
		builder.append(ch); ++i;
		while(i<12){builder.append('z'); ++i;}
		return builder.toString();
	}

	void addObjectiveAndTeam(Player player, int numAdvancements){
		pl.getServer().getScoreboardManager().getMainScoreboard().getObjective("advancements")
			.getScore(player.getName()).setScore(numAdvancements);

		String oldTeamName = getAdvancementTeamName(numAdvancements-1);
		Team oldTeam = player.getScoreboard().getTeam(oldTeamName);
		if(oldTeam != null) oldTeam.removeEntry(player.getName());
		String newTeamName = getAdvancementTeamName(numAdvancements);
		Team newTeam = player.getScoreboard().getTeam(newTeamName);
		if(newTeam == null) newTeam = player.getScoreboard().registerNewTeam(newTeamName);
		newTeam.addEntry(player.getName());
	}

	@EventHandler
	public void onAchievementGet(PlayerAdvancementDoneEvent evt){
		if(!isMainAdvancement(evt.getAdvancement()) || SpectatorManager.isSpectatorFavorYes(evt.getPlayer())) return;
		int advancements = EvUtils.getVanillaAdvancements(evt.getPlayer(), included).size();
		pl.getLogger().info(evt.getPlayer()+" now has "+advancements+" advancements");
		if(advancements == ADV_WL_TRIGGER &&
				!evt.getPlayer().getScoreboardTags().contains("blacklist_mode")){
			evt.getPlayer().addScoreboardTag("whitelist_mode");
		}
		addObjectiveAndTeam(evt.getPlayer(), advancements);
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent evt){
		pl.getServer().getScoreboardManager().getMainScoreboard()
			.getObjective("levels").setDisplaySlot(DisplaySlot.SIDEBAR);
		UUID uuid = evt.getPlayer().getUniqueId();
		if(!XP_ACTIVE) new BukkitRunnable(){@Override public void run(){
			Player player = pl.getServer().getPlayer(uuid);
			if(player != null && !SpectatorManager.isSpectatorFavorYes(player)){
				int advancements = EvUtils.getVanillaAdvancements(player, included).size();
				pl.getLogger().info(player.getName()+" has "+advancements+" advancements");
				addObjectiveAndTeam(player, advancements);
			}
			pl.getServer().getScoreboardManager().getMainScoreboard().clearSlot(DisplaySlot.SIDEBAR);
			XP_ACTIVE = false;
		}}.runTaskLater(pl, 20*5); //5s
		XP_ACTIVE = true;
	}

	boolean XP_ACTIVE = false;
	@EventHandler
	public void onLevelUp(PlayerLevelChangeEvent evt){
		pl.getServer().getScoreboardManager().getMainScoreboard()
			.getObjective("levels").setDisplaySlot(DisplaySlot.SIDEBAR);
		if(evt.getNewLevel() == LVL_WL_TRIGGER &&
				!evt.getPlayer().getScoreboardTags().contains("blacklist_mode")){
			evt.getPlayer().addScoreboardTag("whitelist_mode");
		}
		if(!XP_ACTIVE) new BukkitRunnable(){@Override public void run(){
			pl.getServer().getScoreboardManager().getMainScoreboard().clearSlot(DisplaySlot.SIDEBAR);
			XP_ACTIVE = false;
		}}.runTaskLater(pl, 20*30);
		XP_ACTIVE = true;
	}
}