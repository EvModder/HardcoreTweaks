package Hardcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.HCTweaks;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.ActionComponent;
import net.evmodder.EvLib.extras.TellrawUtils.ClickEvent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.TellrawBlob;
import net.evmodder.EvLib.extras.TextUtils;

public class CommandRegionCleanup extends EvCommand{
	final boolean REQUIRE_CONFIRMATION = true;
	final boolean SHOW_FILE_STATS = true;
	final boolean SHOW_FILE_SZ_QUANTILES = true;
	final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	public CommandRegionCleanup(HCTweaks pl){super(pl);}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		return null;
	}

	long roundToSigFigs(long num, int figs){
		long mult = 1L, fig10 = 10L;
		for(int i=1; i<figs; ++i) fig10 *= 10L;
		while(num >= fig10){
			if(num%10 >= 5) num += 10;
			num /= 10;
			mult *= 10;
		}
		return num*mult;
	}

	long showFilesizeStats(List<World> worlds, CommandSender sender){ // Sender param is optional
		HashMap<Long, Integer> szOccurCount = new HashMap<>();
		ArrayList<Long> fileAges = new ArrayList<>();
		long fileAgeMax = -1;
		for(World world : worlds){
			for(File file : new File(EvUtils.getRegionFolder(world)).listFiles()){
				if(file.isFile() && file.getName().endsWith(".mca")){
					long fileSize = roundToSigFigs(file.length()/1024, 3);
					fileAgeMax = Math.max(fileAgeMax,  fileSize);
					//pl.getLogger().info("sigFig fileSize: "+fileSize+" (regular size: "+(file.length()/1024)+")");
					Integer currentCount = szOccurCount.get(fileSize);
					szOccurCount.put(fileSize, currentCount == null ? 1 : currentCount+1);
					fileAges.add(file.lastModified());
				}
			}
		}
		long mostOccurances = 0, fileSizeMode = -1;
		for(Long fileSz : szOccurCount.keySet()){
			//pl.getLogger().info(szOccurCount.get(fileSz)+" occurances of "+fileSz);
			if(szOccurCount.get(fileSz) > mostOccurances){
				fileSizeMode = fileSz;
				mostOccurances = szOccurCount.get(fileSz);
			}
		}
		Collections.sort(fileAges);
		long fileAgeOldest = fileAges.get(0);
		long fileAgeYoungest = fileAges.get(fileAges.size()-1);
		long fileAgeMedian = fileAges.get(fileAges.size()/2);

		if(sender != null){
			sender.sendMessage("File size max: "+fileAgeMax);
			sender.sendMessage("File size max: "+fileAgeMax);
			sender.sendMessage("File size mode: "+fileSizeMode);
			sender.sendMessage("File age oldest: "+sdf.format(new Date(fileAgeOldest)));
			sender.sendMessage("File age youngest: "+sdf.format(new Date(fileAgeYoungest)));
			sender.sendMessage("File age median: "+sdf.format(new Date(fileAgeMedian)));
		}

		if(SHOW_FILE_SZ_QUANTILES)
		//new Thread(){@Override public void run(){
		for(World w : worlds){
			File folder = new File("./"+w.getName()+"/region/");
			if(!folder.exists()) continue;
			sender.sendMessage("Filesize quantiles for "+w.getName()+" (in KB):");
			File[] files = folder.listFiles();
			long[] fileSizes = new long[folder.listFiles().length];
			for(int i=0; i<files.length; ++i) fileSizes[i] = files[i].length()/1024;
			Arrays.sort(fileSizes);
			sender.sendMessage("Min: "+fileSizes[0]);
			//sender.sendMessage("10%: "+fileSizes[fileSizes.length*1/10 - 1]);
			//sender.sendMessage("20%: "+fileSizes[fileSizes.length*2/10 - 1]);
			sender.sendMessage("25%: "+fileSizes[(int)(fileSizes.length*2.5/10 - 1)]);
			//sender.sendMessage("30%: "+fileSizes[fileSizes.length*3/10 - 1]);
			//sender.sendMessage("40%: "+fileSizes[fileSizes.length*4/10 - 1]);
			sender.sendMessage("50%: "+fileSizes[fileSizes.length*5/10 - 1]);
			//sender.sendMessage("60%: "+fileSizes[fileSizes.length*6/10 - 1]);
			//sender.sendMessage("70%: "+fileSizes[fileSizes.length*7/10 - 1]);
			sender.sendMessage("75%: "+fileSizes[(int)(fileSizes.length*7.5/10 - 1)]);
			//sender.sendMessage("80%: "+fileSizes[fileSizes.length*8/10 - 1]);
			//sender.sendMessage("90%: "+fileSizes[fileSizes.length*9/10 - 1]);
			sender.sendMessage("Max: "+fileSizes[fileSizes.length - 1]);
		}
		//}}.start();
		return fileAgeMedian;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		//cmd: /regioncleanup [world:<name>/all,W=all] [visitlog:true/false,V=true]m [age:2m10d45m,A=1y] [size:Xkb,S=10kb] [confirm] [mock]

		boolean allWorlds = false;
		World world = sender instanceof Player ? ((Player)sender).getWorld() : null;
		boolean hasVisitlog = true;
		long maxAgeInMillis = TextUtils.parseTime("1y");
		long maxKB = 10;
		//boolean requireAllDead = true;
		boolean hasConfirm = !REQUIRE_CONFIRMATION, simulateOnly = false;

		// Read args
		for(String input : args){
			if(input.equalsIgnoreCase("confirm")){hasConfirm = true; continue;}
			if(input.equalsIgnoreCase("mock")){simulateOnly = true; continue;}
			String arg = input;
			int sep = arg.indexOf(':');
			if(sep == -1){
				sender.sendMessage(ChatColor.RED+"Invalid arguments (perhaps missing ':'?)");
				return false;
			}
			String postSep = arg.substring(sep+1);
			if(postSep.isBlank()){
				sender.sendMessage(ChatColor.RED+"Missing value after '"+input.substring(0, sep+1)+"'");
				return false;
			}
			arg = arg.toUpperCase();
			if(arg.startsWith("V")){
				hasVisitlog = Boolean.parseBoolean(postSep);
			}
			else if(arg.startsWith("A")){
				maxAgeInMillis = TextUtils.parseTime(postSep);
			}
			else if(arg.startsWith("S")){
				if(postSep.toLowerCase().endsWith("kb")) postSep = postSep.substring(0, postSep.length()-2);
				maxKB = Long.parseLong(postSep);
			}
			else if(arg.startsWith("W")){
				if(postSep.equalsIgnoreCase("all")) allWorlds = true;
				else{
					world = sender.getServer().getWorld(postSep);
					if(world == null) world = sender.getServer().getWorld(UUID.fromString(postSep));
				}
			}
		}
		if(world == null && !allWorlds){
			sender.sendMessage("Must specify a world (or 'all') in which to run the region cleanup");
			return false;
		}

		final long MAX_FILE_SZ = maxKB;
		final long MAX_FILE_DATE_MILLIS = System.currentTimeMillis() - maxAgeInMillis;
		final List<World> worlds = (allWorlds ? sender.getServer().getWorlds() : Arrays.asList(world));
		final boolean HAS_VISITLOG = hasVisitlog;
		final boolean SIMULATE_ONLY = simulateOnly;
		if(SHOW_FILE_STATS)/* long fileAgeMedian = */showFilesizeStats(worlds, sender);


		if(REQUIRE_CONFIRMATION && !hasConfirm){
			if(sender instanceof Player == false){
				sender.sendMessage(ChatColor.RED+"Add 'confirm' to the command to run the region delete");
				return true;
			}
			StringBuilder commandString = new StringBuilder('/').append(command.getName());
			for(String arg : args) commandString.append(' ').append(arg);

			sender.sendMessage("§c§l§m====================================");
			TellrawBlob blob = new TellrawBlob(
					new RawTextComponent("§6Click to confirm: §7["),
					new ActionComponent("§f>>§cDelete§f<<", ClickEvent.RUN_COMMAND, commandString.toString()),
					new RawTextComponent("§7]")
			);
			HCTweaks.getPlugin().runCommand("minecraft:tellraw "+sender.getName()+" "+blob.toString());
			sender.sendMessage("§c§l§m====================================");

			String maxDateStr = sdf.format(new Date(MAX_FILE_DATE_MILLIS));
			sender.sendMessage(
					"§bDelete §7region .mca§b files IF:\n" +
					"§bFile is no larger than §c"+MAX_FILE_SZ+"§bKB\n" +
					"§bFile hasn't been modified since §c"+maxDateStr+"§b\n" +
					"§bIn world: §e"+(world == null ? "§call" : world.getName())+"§b.\n"+
						"§4[§c§lWarning§4] §6This may delete a lot of your world!\n"+
						"§6Read this first:§7§o http://bukkit.org/reducelag/put-page-here \n ");
			return true;
		}

		sender.sendMessage("§6Staring region-delete...");

		new Thread(){@Override public void run(){
			String todaysDate = sdf.format(new Date());
			File delFolder = new File(FileIO.DIR+"/DELETED/regions "+todaysDate);
			if(!delFolder.mkdir()){
				new File(FileIO.DIR+"/DELETED").mkdir();
				delFolder.mkdir();
			}

			int skipCuzSize = 0, skipCuzAge = 0/*, skipCuzAgeVsCreate = 0, skipCuzError = 0, skipCuzDist = 0*/, skipCuzVisitlog = 0;
			for(World w : worlds){
				String regionFolderStr = EvUtils.getRegionFolder(w);
				File folder = new File(regionFolderStr);
				if(!folder.exists()) continue;
				long deletedKB = 0, numDeleted = 0;

				for(File file : folder.listFiles()){
					if(file.isFile() && file.getName().endsWith(".mca")){
						long fileSize = file.length()/1024;
						if(fileSize > MAX_FILE_SZ){++skipCuzSize; continue;}//<<<<<<<<<<<<<<<

						long lastModified = file.lastModified();
						if(lastModified > MAX_FILE_DATE_MILLIS){++skipCuzAge; continue;}//<<<<<<<<<<<<<<<

						String visitlog = regionFolderStr+file.getName().substring(0, file.getName().length()-4)+".visitlog";
						if(HAS_VISITLOG){
							if(!new File(visitlog).exists()){++skipCuzVisitlog; continue;}//<<<<<<<<<<<<<<<
							else{
								int aliveResidents = 0;
								for(String visit : FileIO.loadFile(visitlog, "").split("\n")){
									int sep = visit.indexOf(',');
									long lastVisit = Long.parseLong(visit.substring(0, sep));
									UUID visitorUUID = UUID.fromString(visit.substring(sep+1));
									OfflinePlayer visitor = sender.getServer().getOfflinePlayer(visitorUUID);
									if(visitor == null) continue; //TODO: print error/warning?
									String lastDeathDateStr = HCTweaks.getLastDeath(visitor.getName()); //TODO: name changes might be bad
									try{
										Date lastDeathDate = sdf.parse(lastDeathDateStr);
										long lastDeath = lastDeathDate.getTime();
										boolean visitedThisLife = lastVisit > lastDeath;
										if(visitedThisLife) ++aliveResidents;
									}
									catch(ParseException e){e.printStackTrace();}
								}
								if(aliveResidents > 0){++skipCuzVisitlog; continue;}//<<<<<<<<<<<<<<<
							}
						}
						/*try{
							BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
							long creationTime = attr.creationTime().toMillis();
							if(creationTime == lastModified) creationTime = 0;// If equal, REAL creation time is unknown :(
							if(lastModified - creationTime > MAX_AGE_VS_CREATION){
								++skipCuzAgeVsCreate; continue;//<<<<<<<<<<<<<<<
							}
						}
						catch(IOException e){
							HCTweaks.getPlugin().getLogger().warning("Unable to read file attributes for "+file.getName());
							++skipCuzError; continue;//<<<<<<<<<<<<<<<
						}*/

						// Perform delete
						if(!SIMULATE_ONLY){
							file.renameTo(new File(delFolder.getPath()+'/'+file.getName()));
							deletedKB += fileSize;
							++numDeleted;
						}
					}
				}
				sender.sendMessage("§6Finished region-delete for world: §c"+w.getName()+"§6.");
				sender.sendMessage("§6Space freed: §c"+deletedKB+"§6KB, §c"+numDeleted+"§6 files");
			}
			sender.sendMessage("§aProcess Complete!");
			sender.sendMessage("Skipped file reasons: "
					+"FileSize:"+skipCuzSize+", FileAge:"+skipCuzAge+", Visitlog:"+skipCuzVisitlog
					/*+", CreationVsModified:"+skipCuzAgeVsCreate
					+", DistFromSpawn:"+skipCuzDist+", Error:"+skipCuzError*/);
		}}.start();
		return true;
	}
}