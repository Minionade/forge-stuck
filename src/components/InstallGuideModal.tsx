import { X, CheckCircle, Download, FolderArchive, Play, AlertCircle } from 'lucide-react';
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
              Follow these simple steps to run your favorite Forge 1.20.1 mods on NeoForge 1.21.1.
            </p>
          </div>

          {/* Steps */}
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-slate-950/80 border border-slate-800 flex gap-3.5">
              <span className="flex items-center justify-center w-6 h-6 rounded-full bg-amber-400/20 text-amber-300 text-xs font-bold shrink-0">
                1
              </span>
              <div className="space-y-1">
                <div className="text-xs font-semibold text-white">
                  Download Forge Stuck!! Mod JAR
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Download the self-contained bridge mod JAR (<span className="text-amber-300 font-mono">{MOD_JAR_FILENAME}</span>).
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

            <div className="p-4 rounded-xl bg-slate-950/80 border border-slate-800 flex gap-3.5">
              <span className="flex items-center justify-center w-6 h-6 rounded-full bg-amber-400/20 text-amber-300 text-xs font-bold shrink-0">
                2
              </span>
              <div className="space-y-1">
                <div className="text-xs font-semibold text-white">
                  Place 1.20.1 Mods inside /mods/.forgestuck/
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Put <span className="text-amber-300 font-mono">{MOD_JAR_FILENAME}</span> in <span className="text-slate-300 font-mono">mods/</span>, and place your 1.20.1 Forge mods inside <span className="text-amber-300 font-mono">mods/.forgestuck/</span>:
                </p>
                <div className="p-2.5 rounded-lg bg-slate-900 border border-slate-800 font-mono text-[11px] text-slate-300 space-y-0.5">
                  <div className="text-slate-500">.minecraft/mods/</div>
                  <div className="text-amber-300 font-medium">├── {MOD_JAR_FILENAME}</div>
                  <div className="text-amber-400 font-medium">└── .forgestuck/</div>
                  <div className="text-emerald-400 pl-4">├── thermal_expansion-1.20.1-10.3.0.jar</div>
                  <div className="text-emerald-400 pl-4">├── cofh_core-1.20.1-10.3.1.jar</div>
                  <div className="text-emerald-400 pl-4">├── gendustry-1.20.1-2.0.4.jar</div>
                  <div className="text-emerald-400 pl-4">└── advgenerators-1.20.1-1.4.2.jar</div>
                </div>
                <p className="text-[11px] text-slate-500 pt-1">
                  *Tip: You can also simply run <code className="text-amber-300">java -jar {MOD_JAR_FILENAME}</code> once in your game folder to automatically organize it!*
                </p>
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-950/80 border border-slate-800 flex gap-3.5">
              <span className="flex items-center justify-center w-6 h-6 rounded-full bg-amber-400/20 text-amber-300 text-xs font-bold shrink-0">
                3
              </span>
              <div className="space-y-1">
                <div className="text-xs font-semibold text-white flex items-center gap-1.5">
                  <Play className="w-3.5 h-3.5 text-emerald-400" />
                  Launch Minecraft NeoForge 1.21.1!
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Start your game. Forge Stuck!! will activate its ASM ClassTransformer during classloading, automatically un-sticking 1.20.1 mods and routing all ItemStack NBT, capabilities, and packets into 1.21.1 equivalents.
                </p>
              </div>
            </div>
          </div>

          {/* Compatibility Tips */}
          <div className="p-3.5 rounded-xl bg-slate-950/40 border border-slate-800/80 text-xs text-slate-400 space-y-1">
            <div className="font-semibold text-slate-300 flex items-center gap-1.5">
              <AlertCircle className="w-4 h-4 text-amber-400" />
              Requirements & Notes
            </div>
            <p>
              • Requires Java 21 (bundled standard with Minecraft 1.21+ launchers).
            </p>
            <p>
              • Requires NeoForge 21.1.0 or newer.
            </p>
            <p>
              • If using Prism Launcher or CurseForge, create a standard NeoForge 1.21.1 profile and simply paste the JARs into the mods folder.
            </p>
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
