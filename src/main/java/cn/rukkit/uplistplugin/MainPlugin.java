package cn.rukkit.uplistplugin;

import cn.rukkit.Rukkit;
import cn.rukkit.command.ChatCommand;
import cn.rukkit.command.ChatCommandListener;
import cn.rukkit.network.Connection;
import cn.rukkit.plugin.RukkitPlugin;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.util.Base64;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

public class MainPlugin extends RukkitPlugin {
	
	private UplistConfig config;
	private Logger log = getLogger();
	private String addData;
	private String updateData;
	private String removeData;
	private String sid;
	private String selfCheckData;
	private boolean isPublished = false;
	private boolean upState = false;
	private ScheduledFuture updateFuture;
	
	private class UpdateTask implements Runnable {
		@Override
		public void run() {
			updateServer();
		}
	}
	
	private final void loadConfig() throws IOException {
		if (config != null) return;
		Yaml yaml = new Yaml(new CustomClassLoaderConstructor(UplistConfig.class, new UplistConfig().getClass().getClassLoader()));
		File confFile = getConfigFile("uplist");
		if (confFile.exists() && confFile.isFile() && confFile.length() > 0) {
			getLogger().debug("Found Config file.Reading...");
		} else {
			getLogger().debug("Config file.not found.Creating...");
			confFile.delete();
			confFile.createNewFile();
			FileWriter writer = new FileWriter(confFile);
			writer.write(yaml.dumpAs(new UplistConfig(), null, DumperOptions.FlowStyle.BLOCK));
			writer.flush();
			writer.close();
		}
		config = yaml.loadAs((new FileInputStream(confFile)), new UplistConfig().getClass());
	}
	
	private String base64ToString(String base64) {
		return new String(Base64.decodeFast(base64));
	}
	
	private void addServer(final Connection conn) {
		FormBody.Builder formBody = new FormBody.Builder();
        formBody.add("Version", "HPS#1");
		OkHttpClient client = new OkHttpClient();
		Request req = new Request.Builder().
						url("https://api.data.der.kim/UpList/v5/upList").
						post(formBody.build()).
						build();
		Call call = client.newCall(req);
		call.enqueue(new Callback() {
				@Override
				public void onFailure(Call p1, IOException p2) {
					conn.sendServerMessage("Network error!");
				}

				@Override
				public void onResponse(Call p1, Response rep) throws IOException {
					String result = rep.body().string();
					if (result.startsWith("[-1]")) {
						conn.sendServerMessage("API Error:" + result);
					} else if (result.startsWith("[-2]")) {
						conn.sendServerMessage("You are in the blacklist:" + result);
					} else {
						JSONObject obj = JSON.parseObject(result);
						sid = base64ToString(obj.getString("id"));
						addData = base64ToString(obj.getString("add"));
						selfCheckData = base64ToString(obj.getString("open"));
						updateData = base64ToString(obj.getString("update"));
						removeData = base64ToString(obj.getString("remove"));
						/*conn.sendServerMessage(String.format("Result: sid=%s, add=%s, open=%s, update=%s, remove=%s",
															sid, addData, selfCheckData, updateData, removeData));*/


						String targetData = addData.replace("{RW-HPS.S.NAME}", config.serverName).
													replace("{RW-HPS.S.PRIVATE.IP}", "10.0.0.1").
													replace("{RW-HPS.S.PORT}", Rukkit.getConfig().serverPort).
													replace("{RW-HPS.RW.MAP.NAME}", Rukkit.getRoundConfig().mapName).
													replace("{RW-HPS.PLAYER.SIZE}", Rukkit.getConnectionManager().size()).
													replace("{RW-HPS.PLAYER.SIZE.MAX}", Rukkit.getConfig().maxPlayer).
													replace("{RW-HPS.RW.VERSION}", "1.14").
													replace("{RW-HPS.RW.VERSION.INT}", "151").
													replace("{RW-HPS.RW.IS.VERSION}", "false").
													replace("{RW-HPS.RW.IS.PASSWD}", "false");
						Callback back = new Callback() {

							@Override
							public void onFailure(Call p1, IOException p2) {
								log.warn("IO Exception:", p2);
							}

							@Override
							public void onResponse(Call p1, Response rep) throws IOException {
								String result = rep.body().string();
								if (result.contains(sid)) {
									upState = true;
									log.info("Server was pubished.");
									log.debug("OK!");
									updateFuture = Rukkit.getThreadManager().schedule(new UpdateTask(), 1000*15, 1000*15);
									conn.sendServerMessage("Server published!");
									isPublished = true;
								} else {
									upState = false;
									log.warn("Server publish failed.");
									log.debug("FAIL!");
									conn.sendServerMessage("Server publish failed!");
								}
							}
						};
						post("http://gs1.corrodinggames.com/masterserver/1.4/interface", targetData, back);
						post("http://gs4.corrodinggames.net/masterserver/1.4/interface", targetData, back);

						String check = selfCheckData.replace("{RW-HPS.S.PORT}", Rukkit.getConfig().serverPort);

						Callback backCheck = new Callback() {

							@Override
							public void onFailure(Call p1, IOException p2) {
								log.warn("IO Exception:", p2);
							}

							@Override
							public void onResponse(Call p1, Response rep) throws IOException {
								String result = rep.body().string();
								if (result.contains("true")) {
									log.info("Server was Port pubished.");
									conn.sendServerMessage("Server Port published!");
								} else {
									log.info("Server was Port not pubished.");
									conn.sendServerMessage("Server Port Not published!");
								}
							}
						};
						post("http://gs1.corrodinggames.com/masterserver/1.4/interface", check, backCheck);
						post("http://gs4.corrodinggames.net/masterserver/1.4/interface", check, backCheck);
					}
				}
			});
			
	}
	
	private FormBody requestToFormBody(String str) {
		FormBody.Builder formBody = new FormBody.Builder();
		final String[] arr = str.split("&");
		for (String pam : arr) {
			final String[] kv = pam.split("=");
			formBody.add(kv[0],kv[1]);
		}
		return formBody.build();
	}
	
	private void post(String url, String data, Callback back) {
		Request req = new Request.Builder().
			url(url).
			post(requestToFormBody(data)).
			build();
		OkHttpClient client = new OkHttpClient();
		Call call = client.newCall(req);
		call.enqueue(back);
	}
	
	private void post(String url, String data) {
		post(url, data, new Callback() {

				@Override
				public void onFailure(Call p1, IOException p2) {
				}

				@Override
				public void onResponse(Call p1, Response p2) throws IOException {
					log.debug("Success: {}", p2.body().string());
				}
			});
	}
	
	private void updateServer() {
		String targetData = updateData.replace("{RW-HPS.S.NAME}", config.serverName).
										replace("{RW-HPS.S.PRIVATE.IP}", "10.0.0.1").
										replace("{RW-HPS.S.PORT}", Rukkit.getConfig().serverPort).
										replace("{RW-HPS.RW.MAP.NAME}", Rukkit.getRoundConfig().mapName).
										replace("{RW-HPS.PLAYER.SIZE}", Rukkit.getConnectionManager().size()).
										replace("{RW-HPS.PLAYER.SIZE.MAX}", Rukkit.getConfig().maxPlayer).
										replace("{RW-HPS.RW.IS.PASSWD}", "false");

		if (!Rukkit.getConfig().nonStopMode) {
			targetData = targetData.replace("{RW-HPS.S.STATUS}", Rukkit.getGameServer().isGaming() ? "ingame": "battleroom");
		} else {
			targetData = targetData.replace("{RW-HPS.S.STATUS}", "battleroom");
		}
		Callback back = new Callback() {

			@Override
			public void onFailure(Call p1, IOException p2) {
				log.warn("IO Exception:", p2);
			}

			@Override
			public void onResponse(Call p1, Response rep) throws IOException {
				String result = rep.body().string();
				if (result.contains("UPDATED")) {
					upState = true;
					log.debug("OK!");
				} else {
					upState = false;
					log.debug("FAIL!");
				}
			}
		};
		post("http://gs1.corrodinggames.com/masterserver/1.4/interface", targetData, back);
		post("http://gs4.corrodinggames.net/masterserver/1.4/interface", targetData, back);
	}
	
	private void stopServer() {
		post("http://gs1.corrodinggames.com/masterserver/1.4/interface", removeData);
		Rukkit.getThreadManager().shutdownTask(updateFuture);
		isPublished = false;
		upState = false;
	}
	
	class PublishCommandListener implements ChatCommandListener {
		@Override
		public boolean onSend(Connection conn, String[] args) {
			if (args.length <= 0) {
				if (isPublished) {
					conn.sendServerMessage("Server already published!");
				} else {
					addServer(conn);
				}
			} else {
				switch (args[0]) {
					case "info":
						conn.sendServerMessage("upState(在线状态)=" + upState + "\n" +
												"isPublished(是否公布)=" + isPublished);
						break;
					case "update":
						conn.sendServerMessage("Updating...");
						updateServer();
						break;
					case "stop":
						conn.sendServerMessage("Stopped!");
						stopServer();
						break;
					case "motd":
						if (args.length >= 2) {
							config.serverName = args[1];
							conn.sendServerMessage("Change complete!");
						} else {
							conn.sendServerMessage("Not enough parameters! 参数不足！");
						}
						break;
					case "help":
					default:
						conn.sendServerMessage("uplist help:\n" +
												".publish info: show currect uplist info. 显示当前服务器公布状态\n" +
												".publish update: update server state now. 更新当前服务器状态\n" +
												".publish stop: stop a uplist. 停止公布服务器\n" +
												".publish motd <motd>: change server motd. 更改服务器MOTD\n");
				}
			}
			return false;
		}
	}
	
	@Override
	public void onLoad() {
		log.info("UplistPluginb:init");
		try {
			loadConfig();
		} catch (IOException e) {
			log.error("Config load failed.Stopped loading.", e);
			throw new RuntimeException("Plugin config load failed.");
		}
		Rukkit.getCommandManager().registerCommand(new ChatCommand("publish", "Publish server.", 2, new PublishCommandListener(), this));
	}
	
	@Override
	public void onEnable() {
		
	}
	
	@Override
	public void onDisable() {
		
	}
	
	@Override
	public void onStart() {
		
	}
	
	@Override
	public void onDone() {
		
	}
}
