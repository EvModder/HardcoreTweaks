package Hardcore.commands;

import org.apache.logging.log4j.util.Strings;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.HCTweaks;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TellrawUtils.TextClickAction;
import net.evmodder.EvLib.extras.TellrawUtils.ClickEvent;
import net.evmodder.EvLib.extras.TellrawUtils.RawTextComponent;
import net.evmodder.EvLib.extras.TellrawUtils.ListComponent;
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
		long fileSizeMax = -1;
		for(World world : worlds){
			for(File file : new File(EvUtils.getRegionFolder(world)).listFiles()){
				if(file.isFile() && file.getName().endsWith(".mca")){
					long fileSize = roundToSigFigs(file.length()/1024, 3);
					fileSizeMax = Math.max(fileSizeMax,  fileSize);
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
			sender.sendMessage("Num files: "+fileAges.size());
			sender.sendMessage("File size max: §c"+fileSizeMax+"§r, mode: §c"+fileSizeMode);
			sender.sendMessage("File age range: [ §c"+sdf.format(new Date(fileAgeOldest))+"§r to §c"+sdf.format(new Date(fileAgeYoungest))+"§r ]");
			sender.sendMessage("File age median: §c"+sdf.format(new Date(fileAgeMedian)));
		}

		if(SHOW_FILE_SZ_QUANTILES)
		//new Thread(){@Override public void run(){
		for(World w : worlds){
			File folder = new File("./"+w.getName()+"/region/");
			if(!folder.exists()) continue;
			sender.sendMessage("Filesize quantiles (in KB):");
			File[] files = folder.listFiles();
			long[] fileSizes = new long[folder.listFiles().length];
			for(int i=0; i<files.length; ++i) fileSizes[i] = files[i].length()/1024;
			Arrays.sort(fileSizes);
			long q0 = fileSizes[0];
			long q25 = fileSizes[(int)(fileSizes.length*2.5/10 - 1)];
			long q50 = fileSizes[fileSizes.length*5/10 - 1];
			long q75 = fileSizes[(int)(fileSizes.length*7.5/10 - 1)];
			long q100 = fileSizes[fileSizes.length - 1];
			sender.sendMessage("Min: §c"+q0+" §e>>§r 25%: §c"+q25+" §e>>§r 50%: §c"+q50+" §e>>§r 75%: §c"+q75+" §e>>§r Max: §c"+q100);
			//sender.sendMessage("Min: "+fileSizes[0]);
			/*//sender.sendMessage("10%: "+fileSizes[fileSizes.length*1/10 - 1]);
			//sender.sendMessage("20%: "+fileSizes[fileSizes.length*2/10 - 1]);
			sender.sendMessage("25%: "+fileSizes[(int)(fileSizes.length*2.5/10 - 1)]);
			//sender.sendMessage("30%: "+fileSizes[fileSizes.length*3/10 - 1]);
			//sender.sendMessage("40%: "+fileSizes[fileSizes.length*4/10 - 1]);
			sender.sendMessage("50%: "+fileSizes[fileSizes.length*5/10 - 1]);
			//sender.sendMessage("60%: "+fileSizes[fileSizes.length*6/10 - 1]);
			//sender.sendMessage("70%: "+fileSizes[fileSizes.length*7/10 - 1]);
			sender.sendMessage("75%: "+fileSizes[(int)(fileSizes.length*7.5/10 - 1)]);
			//sender.sendMessage("80%: "+fileSizes[fileSizes.length*8/10 - 1]);
			//sender.sendMessage("90%: "+fileSizes[fileSizes.length*9/10 - 1]);*/
			//sender.sendMessage("Max: "+fileSizes[fileSizes.length - 1]);
		}
		//}}.start();
		return fileAgeMedian;
	}

	private boolean deleteInProgress = false;
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		//cmd: /regioncleanup [world:<name>/all,W=all] [visitlog:true/false,V=true]m [age:2m10d45m,A=1y] [size:Xkb,S=10kb] [confirm] [mock]

		boolean allWorlds = false;
		World world = sender instanceof Player ? ((Player)sender).getWorld() : null;
		boolean hasVisitlog = true;
		long maxAgeInMillis = TextUtils.parseTime("1y");
		long maxKB = 1000;
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
			StringBuilder commandString = new StringBuilder("/").append(command.getName());
			for(String arg : args) commandString.append(' ').append(arg);

			if(!simulateOnly){
				sender.sendMessage("§c§l§m====================================");
				ListComponent blob = new ListComponent(
						new RawTextComponent("§6Click to confirm: §7["),
						new RawTextComponent("§f>>§4§lDelete§f<<", new TextClickAction(ClickEvent.RUN_COMMAND, commandString.toString()+" confirm")),
						new RawTextComponent("§7]")
				);
				HCTweaks.getPlugin().getLogger().info("tellraw blob: "+blob.toString());
				HCTweaks.getPlugin().runCommand("minecraft:tellraw "+sender.getName()+" "+blob.toString());
				sender.sendMessage("§c§l§m====================================");
			}

			String maxDateStr = sdf.format(new Date(MAX_FILE_DATE_MILLIS));
			sender.sendMessage(
					"§bDelete §7region .mca§b files IF:\n" +
					"§bFile is no larger than §c"+MAX_FILE_SZ+"§bKB\n" +
					"§bFile hasn't been modified since §c"+maxDateStr+"§b\n" +
					"§bFile "+(HAS_VISITLOG ? "§cHAS" : "does §cNOT§b have")+"§b a visit log\n"+
					"§bIn world: §e"+Strings.join(worlds.stream().map(w -> w.getName()).collect(Collectors.toList()), ',')+"§b.");

			if(!simulateOnly){
				sender.sendMessage("\n§4[§c§lWarning§4] §6This may delete a lot of your world map!\n"+
									"§6Read this first:§7§o http://bukkit.org/reducelag/put-page-here \n ");
				return true;
			}
		}
		if(deleteInProgress){
			sender.sendMessage("§cA region-delete process is already in progress, please wait for it to complete before running another");
			return true;
		}
		deleteInProgress = true;

		sender.sendMessage("§6Staring region-delete...");

		new Thread(){@Override public void run(){
			String todaysDate = sdf.format(new Date());
			File delFolder = new File(FileIO.DIR+"/DELETED/regions "+todaysDate);
			if(!delFolder.mkdir()){
				new File(FileIO.DIR+"/DELETED").mkdir();
				delFolder.mkdir();
			}

			int skipCuzSize = 0, skipCuzAge = 0/*, skipCuzAgeVsCreate = 0, skipCuzDist = 0*/, skipCuzError = 0,
					skipCuzVisitlog = 0, skipCuzActiveVisitlog = 0;
			long totalDeletedKB = 0, totalNumDeleted = 0;
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
						boolean hasVisitlog = new File(visitlog).exists();
						if(HAS_VISITLOG && !hasVisitlog){++skipCuzVisitlog; continue;}//<<<<<<<<<<<<<<<
						else if(!HAS_VISITLOG && hasVisitlog){++skipCuzVisitlog; continue;}//<<<<<<<<<<<<<<<
						if(HAS_VISITLOG && hasVisitlog){
							String visitFileContents = "";
							try{visitFileContents = new String(Files.readAllBytes(Paths.get(visitlog)));}
							catch(IOException e1){e1.printStackTrace();}
							int aliveResidents = 0;
							String[] visitLines = visitFileContents.split("\n");
							for(String visit : visitLines){
								//TODO: temp fix code /////////////////////
								if(visit.equals(visitLines[visitLines.length-1])){
									if(!visit.startsWith("created:")){
										if(visit.contains("created:")){
											try{
												BufferedWriter writer = new BufferedWriter(new FileWriter(visitlog));
												writer.write(visitFileContents.replace("created:", "\ncreated:")); writer.close();
											}catch(IOException e){}
											HCTweaks.getPlugin().getLogger().severe("fixed bad visitlog creation ts!");
										}
										else{
											try{
												BufferedWriter writer = new BufferedWriter(new FileWriter(visitlog));
												writer.write(visitFileContents+"\ncreated:"+0); writer.close();
											}catch(IOException e){}
											HCTweaks.getPlugin().getLogger().severe("fixed missing visitlog creation ts!");
										}
									}
								}
								///////////////////////////////////////////
								if(visit.startsWith("created:")) continue; // or break;, since this should always be the last line
								int sep = visit.indexOf(',');
								if(sep == -1){
									HCTweaks.getPlugin().getLogger().warning("sep is -1!\n"+ "file: "+visitFileContents+"\n, line: "+visit);
									HCTweaks.getPlugin().getLogger().warning("file name: "+visitlog);
									++skipCuzError; continue;
								}
								try{
								long lastVisit = Long.parseLong(visit.substring(0, sep));
								UUID visitorUUID = UUID.fromString(visit.substring(sep+1));
								OfflinePlayer visitor = sender.getServer().getOfflinePlayer(visitorUUID);
								if(visitor == null) continue; //TODO: print error/warning?
								String lastDeathDateStr = HCTweaks.getLastDeath(visitor.getName()); //TODO: name changes might be bad
								try{
									long lastDeath = lastDeathDateStr.equalsIgnoreCase("None")
											? Long.MAX_VALUE : sdf.parse(lastDeathDateStr).getTime();
									boolean visitedThisLife = lastVisit > lastDeath;
									if(visitedThisLife) ++aliveResidents;
								}
								catch(ParseException e){e.printStackTrace();}
								}catch(Exception e){
									HCTweaks.getPlugin().getLogger().warning("visit: '"+visit+"'");
									HCTweaks.getPlugin().getLogger().warning("file name: "+visitlog);
									++skipCuzError; continue;
								}
							}
							if(aliveResidents > 0){++skipCuzActiveVisitlog; continue;}//<<<<<<<<<<<<<<<
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
							if(hasVisitlog){
								File visitlogFile = new File(visitlog);
								visitlogFile.renameTo(new File(delFolder.getPath()+'/'+visitlogFile.getName()));
							}
						}
						deletedKB += fileSize;
						++numDeleted;
					}
				}
				sender.sendMessage("§6Finished region-delete for world: §c"+w.getName()+"§6.");
				sender.sendMessage("§6Space freed: §c"+deletedKB+"§6KB, §c"+numDeleted+"§6 files");
				totalDeletedKB += deletedKB;
				totalNumDeleted += numDeleted;
			}
			sender.sendMessage("§aProcess Complete!");
			sender.sendMessage("Deleted kb:"+totalDeletedKB+", files:"+totalNumDeleted);
			sender.sendMessage("Skip reasons: "
					+"FileSize:"+skipCuzSize+", FileAge:"+skipCuzAge+", HasVisitLog:"+skipCuzVisitlog
					+", ActiveVisitLog: "+skipCuzActiveVisitlog
					/*+", CreationVsModified:"+skipCuzAgeVsCreate
					+", DistFromSpawn:"+skipCuzDist*/+", Error:"+skipCuzError);
			deleteInProgress = false;
		}}.start();
		return true;
	}
}