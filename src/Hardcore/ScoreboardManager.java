package Hardcore;

import java.util.HashSet;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Team;
import net.evmodder.EvLib.EvUtils;


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

		/*
		Scoreboard mainBoard = pl.getServer().getScoreboardManager().getMainScoreboard();
		mainBoard.registerNewObjective("buildscore", "dummy", "§[■] Blocks Placed [■]");
		mainBoard.registerNewObjective("advancements", "dummy ", "");
		mainBoard.registerNewObjective("deaths", "deathCount", "");
		mainBoard.registerNewObjective("murderscore", "playerKillCount ", "");
		mainBoard.registerNewObjective("levels", "level", "§e- §bLevels §e-");
		mainBoard.registerNewObjective("health", "health", "Health");
		//*/
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

		boolean wasUpdated = false;
		String oldTeamName = getAdvancementTeamName(numAdvancements-1);
		Team oldTeam = player.getScoreboard().getTeam(oldTeamName);
		if(oldTeam != null) wasUpdated |= oldTeam.removeEntry(player.getName());
		String newTeamName = getAdvancementTeamName(numAdvancements);
		Team newTeam = player.getScoreboard().getTeam(newTeamName);
		if(newTeam == null) newTeam = player.getScoreboard().registerNewTeam(newTeamName);
		wasUpdated |= (!newTeam.hasEntry(player.getName()));
		newTeam.addEntry(player.getName());
		if(wasUpdated){//TODO: Does this actually fix the issue?
			Plugin btlp = pl.getServer().getPluginManager().getPlugin("BungeeTabListPlus");
			if(btlp != null){
				pl.getServer().getPluginManager().disablePlugin(btlp);
				pl.getServer().getPluginManager().enablePlugin(btlp);
			}
		}
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

	private static boolean SIDEBAR_ACTIVE = false;
	public static void showOnSidebar(String objective, int seconds){
		HCTweaks pl = HCTweaks.getPlugin();
		pl.getServer().getScoreboardManager().getMainScoreboard().getObjective(objective).setDisplaySlot(DisplaySlot.SIDEBAR);

		if(!SIDEBAR_ACTIVE){
			SIDEBAR_ACTIVE = true;
			new BukkitRunnable(){@Override public void run(){
				pl.getServer().getScoreboardManager().getMainScoreboard().clearSlot(DisplaySlot.SIDEBAR);
				SIDEBAR_ACTIVE = false;
			}}.runTaskLater(pl, 20*seconds);
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent evt){
		//showOnSidebar("levels", 5);//TODO: remove
		UUID uuid = evt.getPlayer().getUniqueId();
		new BukkitRunnable(){@Override public void run(){
			Player player = pl.getServer().getPlayer(uuid);
			if(player != null && !SpectatorManager.isSpectatorFavorYes(player)){
				int advancements = EvUtils.getVanillaAdvancements(player, included).size();
				pl.getLogger().info(player.getName()+" has "+advancements+" advancements");
				addObjectiveAndTeam(player, advancements);
			}
		}}.runTaskLater(pl, 20*5); //5s
	}

	@EventHandler
	public void onLevelUp(PlayerLevelChangeEvent evt){
		//showOnSidebar("levels", 30);//TODO: remove
		if(evt.getNewLevel() == LVL_WL_TRIGGER && !evt.getPlayer().getScoreboardTags().contains("blacklist_mode")){
			evt.getPlayer().addScoreboardTag("whitelist_mode");
		}
	}

	class Coord{
		int x, y, z;
		Coord(int a, int b, int c){x=a; y=b; z=c;} 
		@Override public boolean equals(Object o){return o instanceof Coord &&
				((Coord)o).x == x && ((Coord)o).y == y && ((Coord)o).z == z;}
		@Override public int hashCode(){return x * y * z;}
	}
	private HashSet<Coord> blockPlacedCoords = new HashSet<Coord>();
	@EventHandler
	public void onBlockPlaced(BlockPlaceEvent evt){
		Block b = evt.getBlock();
		if(b.isLiquid() || b.isPassable() || !b.getType().isSolid()
				|| evt.getBlockReplacedState().getType() != Material.AIR) return;
		if(!blockPlacedCoords.add(new Coord(b.getX(), b.getY(), b.getZ()))) return;

		String name10 = evt.getPlayer().getName();
		if(name10.length() > 10) name10 = name10.substring(0, 10);
		Score buildScore = pl.getServer().getScoreboardManager().getMainScoreboard()
				.getObjective("buildscore").getScore(name10);
		switch(evt.getBlock().getType()){
			case NETHERRACK:
			case DIRT: case GRASS_BLOCK:
			case COBBLESTONE:
			case STONE: case STONE_BRICKS:
			case ACACIA_LEAVES: case BIRCH_LEAVES: case DARK_OAK_LEAVES:
			case JUNGLE_LEAVES: case OAK_LEAVES: case SPRUCE_LEAVES:
			case ACACIA_PLANKS: case BIRCH_PLANKS: case DARK_OAK_PLANKS:
			case JUNGLE_PLANKS: case OAK_PLANKS: case SPRUCE_PLANKS:
				buildScore.setScore(buildScore.getScore() + 1);
				break;
			case OBSIDIAN:
			case END_STONE:
			case GLASS:
			case BLACK_STAINED_GLASS:
			case BLUE_STAINED_GLASS:
			case BROWN_STAINED_GLASS:
			case CYAN_STAINED_GLASS:
			case GRAY_STAINED_GLASS:
			case GREEN_STAINED_GLASS:
			case LIGHT_BLUE_STAINED_GLASS:
			case LIGHT_GRAY_STAINED_GLASS:
			case LIME_STAINED_GLASS:
			case MAGENTA_STAINED_GLASS:
			case ORANGE_STAINED_GLASS:
			case PINK_STAINED_GLASS:
			case PURPLE_STAINED_GLASS:
			case RED_STAINED_GLASS:
			case WHITE_STAINED_GLASS:
			case YELLOW_STAINED_GLASS:
			case QUARTZ_BLOCK:
			case QUARTZ_PILLAR:
			case BLUE_ICE:
				buildScore.setScore(buildScore.getScore() + 3);
				break;
			default:
				if(b.getType().isOccluding())
					buildScore.setScore(buildScore.getScore() + 2);
				else
					buildScore.setScore(buildScore.getScore() + 1);
		}
	}

}