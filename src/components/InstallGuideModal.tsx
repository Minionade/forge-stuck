import { X, Download, Play, AlertCircle, FolderOpen, Zap } from 'lucide-react';
import { downloadForgeStuckJar, MOD_JAR_FILENAME } from '../utils/zipExporter';

interface InstallGuideModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function InstallGuideModal({ isOpen, onClose }: InstallGuideModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="w-full max-w-2xl bg-slate-900 border border-slate-700/80 rounded-2xl p-6 shadow-2xl relative max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={onClose}
          className="absolute top-5 right-5 p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="space-y-6">
          {/* Header */}
          <div>
            <div className="text-xs font-semibold text-amber-400 uppercase tracking-wider font-mono">
              Quick Setup Guide
            </div>
            <h2 className="text-xl font-display font-bold text-white mt-1">
              How to Use Forge Stuck!! in NeoForge 1.21.1
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Two files. One folder. Hit launch. That's it — just like Sinytra Connector or RetroMod.
            </p>
          </div>

          {/* How it works banner */}
          <div className="p-3.5 rounded-xl bg-amber-400/5 border border-amber-400/20 flex gap-3 items-start">
            <Zap className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <p className="text-xs text-slate-300 leading-relaxed">
              <span className="text-amber-300 font-semibold">No subfolders. No file moving. No extra steps.</span>
              {' '}Forge Stuck!! registers a custom <code className="text-cyan-300 font-mono">IModFileReader</code> via ServiceLoader that intercepts your 1.20.1 mod JARs <em>before</em> NeoForge's default reader can reject them — synthesizing a valid{' '}
              <code className="text-cyan-300 font-mono">neoforge.mods.toml</code> in-memory and handing them off cleanly.
            </p>
          </div>

          {/* Steps */}
          <div className="space-y-4">
            {/* Step 1 */}
            <div className="p-4 rounded-xl bg-slate-950/80 border border-slate-800 flex gap-3.5">
              <span className="flex items-center justify-center w-6 h-6 rounded-full bg-amber-400/20 text-amber-300 text-xs font-bold shrink-0">
                1
              </span>
              <div className="space-y-1.5">
                <div className="text-xs font-semibold text-white flex items-center gap-1.5">
                  <Download className="w-3.5 h-3.5 text-amber-400" />
                  Download Forge Stuck!! Mod JAR
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Grab the self-contained bridge mod (<span className="text-amber-300 font-mono">{MOD_JAR_FILENAME}</span>).
                </p>
                <div className="pt-1">
                  <button
                    onClick={downloadForgeStuckJar}
                    className="inline-flex items-center gap-1.5 px-3 py-1 text-xs font-semibold rounded-md bg-amber-400 text-slate-950 hover:bg-amber-300 transition-colors"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>Download {MOD_JAR_FILENAME}</span>
                  </button>
                </div>
              </div>
            </div>

            {/* Step 2 */}
            <div className="p-4 rounded-xl bg-slate-950/80 border border-slate-800 flex gap-3.5">
              <span className="flex items-center justify-center w-6 h-6 rounded-full bg-amber-400/20 text-amber-300 text-xs font-bold shrink-0">
                2
              </span>
              <div className="space-y-1.5">
                <div className="text-xs font-semibold text-white flex items-center gap-1.5">
                  <FolderOpen className="w-3.5 h-3.5 text-cyan-400" />
                  Drop everything into <code className="font-mono text-slate-300">mods/</code> — flat
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Put <span className="text-amber-300 font-mono">{MOD_JAR_FILENAME}</span> and all your 1.20.1 Forge mods directly in the same <span className="text-slate-300 font-mono">mods/</span> folder. No subfolder needed.
                </p>
                <div className="p-2.5 rounded-lg bg-slate-900 border border-slate-800 font-mono text-[11px] text-slate-300 space-y-0.5">
                  <div className="text-slate-500">.minecraft/mods/</div>
                  <div className="text-amber-300 font-medium">├── {MOD_JAR_FILENAME}</div>
                  <div className="text-emerald-400">├── thermal_expansion-1.20.1-10.3.0.jar</div>
                  <div className="text-emerald-400">├── cofh_core-1.20.1-10.3.1.jar</div>
                  <div className="text-emerald-400">├── gendustry-1.20.1-2.0.4.jar</div>
                  <div className="text-emerald-400">└── advgenerators-1.20.1-1.4.2.jar</div>
                </div>
                <p className="text-[11px] text-slate-500 pt-0.5">
                  Your original JAR files are never moved, copied, or modified on disk.
                </p>
              </div>
            </div>

            {/* Step 3 */}
            <div className="p-4 rounded-xl bg-slate-950/80 border border-slate-800 flex gap-3.5">
              <span className="flex items-center justify-center w-6 h-6 rounded-full bg-emerald-400/20 text-emerald-300 text-xs font-bold shrink-0">
                3
              </span>
              <div className="space-y-1">
                <div className="text-xs font-semibold text-white flex items-center gap-1.5">
                  <Play className="w-3.5 h-3.5 text-emerald-400" />
                  Launch Minecraft NeoForge 1.21.1!
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Start your game. Forge Stuck!!'s <code className="text-cyan-300 font-mono">ForgeStuckModFileReader</code> intercepts your 1.20.1 JARs during mod discovery, synthesizes a <code className="text-cyan-300 font-mono">neoforge.mods.toml</code> in-memory, and hands them to NeoForge — all before the "Skipping jar..." rejection can fire. The ASM transformer then patches runtime bytecode (NBT → DataComponents, capabilities, network, registries) on the fly.
                </p>
              </div>
            </div>
          </div>

          {/* Requirements */}
          <div className="p-3.5 rounded-xl bg-slate-950/40 border border-slate-800/80 text-xs text-slate-400 space-y-1">
            <div className="font-semibold text-slate-300 flex items-center gap-1.5">
              <AlertCircle className="w-4 h-4 text-amber-400" />
              Requirements
            </div>
            <p>• Requires Java 21 (bundled with all Minecraft 1.21+ launchers).</p>
            <p>• Requires NeoForge 21.1.0 or newer.</p>
            <p>• Works with Prism Launcher, CurseForge, ATLauncher, and vanilla launcher — just paste the JARs into the mods folder of a NeoForge 1.21.1 instance.</p>
          </div>

          <div className="pt-2 flex justify-end">
            <button
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold rounded-lg bg-slate-800 hover:bg-slate-700 text-white transition-colors"
            >
              Got it, close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
