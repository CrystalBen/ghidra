/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.terminal;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import ghidra.app.CorePluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.core.terminal.vt.VtOutput;
import ghidra.app.services.*;
import ghidra.framework.plugintool.*;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.util.Msg;
import ghidra.util.Swing;

/**
 * The plugin that provides {@link TerminalService}
 */
@PluginInfo(
	status = PluginStatus.UNSTABLE,
	category = PluginCategoryNames.COMMON,
	packageName = CorePluginPackage.NAME,
	description = "Provides VT100 Terminal Emulation",
	shortDescription = "VT100 Emulator",
	servicesProvided = { TerminalService.class })
public class TerminalPlugin extends Plugin implements TerminalService {

	protected ClipboardService clipboardService;

	protected List<TerminalProvider> providers = new ArrayList<>();

	public TerminalPlugin(PluginTool tool) {
		super(tool);
		clipboardService = tool.getService(ClipboardService.class);
	}

	public TerminalProvider createProvider(Charset charset, VtOutput outputCb) {
		return Swing.runNow(() -> {
			TerminalProvider provider = new TerminalProvider(this, charset);
			provider.setOutputCallback(outputCb);
			provider.addToTool();
			provider.setVisible(true);
			providers.add(provider);
			provider.setClipboardService(clipboardService);
			return provider;
		});
	}

	@Override
	public Terminal createNullTerminal(Charset charset, VtOutput outputCb) {
		return new DefaultTerminal(createProvider(charset, outputCb));
	}

	@Override
	public Terminal createWithStreams(Charset charset, InputStream in, OutputStream out) {
		WritableByteChannel channel = Channels.newChannel(out);
		return new ThreadedTerminal(createProvider(charset, buf -> {
			while (buf.hasRemaining()) {
				try {
					//ThreadedTerminal.printBuffer(">> ", buf);
					channel.write(buf);
				}
				catch (IOException e) {
					Msg.error(this, "Could not write terminal output", e);
				}
			}
		}), in);
	}

	@Override
	public void serviceAdded(Class<?> interfaceClass, Object service) {
		if (interfaceClass == ClipboardService.class) {
			clipboardService = (ClipboardService) service;
			for (TerminalProvider p : providers) {
				p.setClipboardService(clipboardService);
			}
		}
	}

	@Override
	public void serviceRemoved(Class<?> interfaceClass, Object service) {
		if (interfaceClass == ClipboardService.class) {
			for (TerminalProvider p : providers) {
				p.setClipboardService(null);
			}
		}
	}
}
