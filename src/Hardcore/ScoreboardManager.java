package Hardcore;

import java.util.HashSet;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import net.evmodder.EvLib.bukkit.EvUtils;

public class ScoreboardManager implements Listener{
	final HashSet<String> included;
	final int ADV_WL_TRIGGER, LVL_WL_TRIGGER;
	final HCTweaks pl;

	public ScoreboardManager(HCTweaks plugin){
		pl = plugin;
		included = new HashSet<String>();
		included.addAll(pl.getConfig().getStringList("advancements-included"));
		ADV_WL_TRIGGER = pl.getConfig().getInt("set-whitelist-mode-when-has-x-advancements", 15);
		LVL_WL_TRIGGER = pl.getConfig().getInt("set-whitelist-mode-when-has-x-levels", 50);
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

	static void setNumDeaths(Player player, int numDeaths){
		org.bukkit.Bukkit.getServer().getScoreboardManager().getMainScoreboard()
			.getObjective("deaths").getScore(player.getName()).setScore(numDeaths);
	}

	public static void resetScores(Player player){
		String name = player.getName();
		String name10 = name.length() > 10 ? name.substring(0, 10) : name;
		int numDeaths = player.getScoreboard().getObjective("deaths").getScore(name).getScore();
		int buildScore = player.getScoreboard().getObjective("buildscore").getScore(name10).getScore();
		player.getScoreboard().resetScores(name);
		if(name.length() > 10) player.getScoreboard().resetScores(name10);
		setNumDeaths(player, numDeaths);
		player.getScoreboard().getObjective("buildscore").getScore(name10).setScore(buildScore);//save
	}

//	private void setBungeeTabTeam(Player player, int numAdvancements){
////		pl.getServer().getScoreboardManager().getMainScoreboard().getObjective("advancements")
////			.getScore(player.getName()).setScore(numAdvancements);
//
//		boolean wasUpdated = false;
//		String oldTeamName = getAdvancementTeamName(numAdvancements-1);
//		Team oldTeam = player.getScoreboard().getTeam(oldTeamName);
//		if(oldTeam != null) wasUpdated |= oldTeam.removeEntry(player.getName());
//		String newTeamName = getAdvancementTeamName(numAdvancements);
//		Team newTeam = player.getScoreboard().getTeam(newTeamName);
//		if(newTeam == null) newTeam = player.getScoreboard().registerNewTeam(newTeamName);
//		wasUpdated |= (!newTeam.hasEntry(player.getName()));
//		newTeam.addEntry(player.getName());
//		if(wasUpdated){//TODO: Does this actually fix the issue?
//			Plugin btlp = pl.getServer().getPluginManager().getPlugin("BungeeTabListPlus");
//			if(btlp != null){
//				pl.getServer().getPluginManager().disablePlugin(btlp);
//				pl.getServer().getPluginManager().enablePlugin(btlp);
//			}
//		}
//	}

	@EventHandler
	public void onAchievementGet(PlayerAdvancementDoneEvent evt){
		if(!isMainAdvancement(evt.getAdvancement()) || SpectatorManager.isSpectatorFavorYes(evt.getPlayer())) return;
		int advancements = EvUtils.getVanillaAdvancements(evt.getPlayer(), included).size();
		pl.getLogger().info(evt.getPlayer()+" now has "+advancements+" advancements");
		if(advancements == ADV_WL_TRIGGER && !evt.getPlayer().getScoreboardTags().contains("blacklist_mode")){
			evt.getPlayer().addScoreboardTag("whitelist_mode");
		}
		//TODO: re-enable this somehow:
		//setBungeeTabTeam(evt.getPlayer(), advancements);
	}

	private static boolean SIDEBAR_ACTIVE = false;
	public static void showOnSidebar(String objective, int seconds){
		final HCTweaks pl = HCTweaks.getPlugin();
		final Scoreboard mainBoard = pl.getServer().getScoreboardManager().getMainScoreboard();
		mainBoard.getObjective(objective).setDisplaySlot(DisplaySlot.SIDEBAR);

		if(!SIDEBAR_ACTIVE){
			SIDEBAR_ACTIVE = true;
			new BukkitRunnable(){@Override public void run(){
				mainBoard.clearSlot(DisplaySlot.SIDEBAR);
				SIDEBAR_ACTIVE = false;
			}}.runTaskLater(pl, 20*seconds);
		}
	}

//	@EventHandler
//	public void onPlayerJoin(PlayerJoinEvent evt){
//		//showOnSidebar("levels", 5);//TODO: remove
//		final UUID uuid = evt.getPlayer().getUniqueId();
//		new BukkitRunnable(){@Override public void run(){
//			Player player = pl.getServer().getPlayer(uuid);
//			if(player != null && !SpectatorManager.isSpectatorFavorYes(player)){
//				final int advancements = EvUtils.getVanillaAdvancements(player, included).size();
//				//DEBUG: pl.getLogger().info(player.getName()+" has "+advancements+" advancements");
//				setBungeeTabTeam(player, advancements);
//			}
//		}}.runTaskLater(pl, 20*5); //5s
//	}

	@EventHandler
	public void onLevelUp(PlayerLevelChangeEvent evt){
		//showOnSidebar("levels", 30);//TODO: remove
		if(evt.getNewLevel() == LVL_WL_TRIGGER && !evt.getPlayer().getScoreboardTags().contains("blacklist_mode")){
			evt.getPlayer().addScoreboardTag("whitelist_mode");
		}
	}
}