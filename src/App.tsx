import { useState } from 'react';
import { Header } from './components/Header';
import { ModSelector } from './components/ModSelector';
import { CompatibilityDashboard } from './components/CompatibilityDashboard';
import { InteractiveSimulator } from './components/InteractiveSimulator';
import { CodeViewer } from './components/CodeViewer';
import { InstallGuideModal } from './components/InstallGuideModal';
import { TARGET_MOD_PROFILES, ModProfile } from './data/modProfiles';
import { Sparkles, Layers, Cpu, ShieldCheck, Download, BookOpen } from 'lucide-react';
import { downloadForgeStuckJar, MOD_JAR_FILENAME } from './utils/zipExporter';

export default function App() {
  const [activeTab, setActiveTab] = useState<'analyzer' | 'sandbox' | 'source' | 'install'>('analyzer');
  const [selectedMod, setSelectedMod] = useState<ModProfile>(TARGET_MOD_PROFILES[0]);
  const [customMods, setCustomMods] = useState<ModProfile[]>([]);
  const [isInstallGuideOpen, setIsInstallGuideOpen] = useState(false);

  const handleAddCustomMod = (mod: ModProfile) => {
    setCustomMods((prev) => [mod, ...prev]);
  };

  return (
    <div className="min-h-screen bg-[#0B0F17] text-slate-100 flex flex-col font-sans selection:bg-amber-500/20 selection:text-amber-300">
      {/* 3-Zone Top Navigation Bar */}
      <Header
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        onOpenInstallGuide={() => setIsInstallGuideOpen(true)}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6 sm:py-8 space-y-8">
        {/* Hero Section */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 pb-6 border-b border-slate-800/80">
          <div className="space-y-2 max-w-3xl">
            <div className="inline-flex items-center gap-2 text-xs font-mono text-amber-400 font-semibold tracking-wide uppercase">
              <span className="w-2 h-2 rounded-full bg-amber-400" />
              <span>NeoForge 1.21.1 Runtime Compatibility Patcher</span>
            </div>
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-display font-extrabold text-white tracking-tight leading-[1.15]">
              Forge Stuck!!
            </h1>
            <p className="text-sm sm:text-base text-slate-400 leading-relaxed max-w-2xl">
              For when your favorite mods are stubbornly stuck in 1.20.1! A drop-in NeoForge 1.21.1 mod that uses runtime bytecode transformation and dynamic shims to un-stick Forge 1.20.1 mods—with targeted deep support for Thermal Series, Gendustry CE, and Advanced Generators.
            </p>
          </div>

          <div className="flex flex-wrap sm:flex-nowrap items-center gap-3 shrink-0">
            <button
              onClick={() => setIsInstallGuideOpen(true)}
              className="px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-800 text-xs font-semibold flex items-center gap-2 transition-colors whitespace-nowrap"
            >
              <BookOpen className="w-4 h-4 text-slate-400" />
              <span>Installation Guide</span>
            </button>
            <button
              onClick={downloadForgeStuckJar}
              className="px-4 py-2.5 rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-bold flex items-center gap-2 transition-all shadow-lg shadow-amber-500/10 whitespace-nowrap"
            >
              <Download className="w-4 h-4" />
              <span>Download {MOD_JAR_FILENAME}</span>
            </button>
          </div>
        </div>

        {/* 4-Item KPI Strip */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div className="p-3.5 rounded-xl bg-slate-900/50 border border-slate-800/80 space-y-1">
            <div className="text-[11px] font-mono uppercase text-slate-400">Target Platform</div>
            <div className="text-base font-bold font-mono text-white">NeoForge 1.21.1</div>
            <div className="text-[11px] text-slate-400">Java 21 · MDK 21.1.72</div>
          </div>
          <div className="p-3.5 rounded-xl bg-slate-900/50 border border-slate-800/80 space-y-1">
            <div className="text-[11px] font-mono uppercase text-slate-400">Source Compatibility</div>
            <div className="text-base font-bold font-mono text-amber-400">Forge 1.20.1</div>
            <div className="text-[11px] text-slate-400">Bytecode Auto-Remapping</div>
          </div>
          <div className="p-3.5 rounded-xl bg-slate-900/50 border border-slate-800/80 space-y-1">
            <div className="text-[11px] font-mono uppercase text-slate-400">Primary Targets</div>
            <div className="text-base font-bold font-mono text-emerald-400">3 Deep Profiles</div>
            <div className="text-[11px] text-slate-400">Thermal · Gendustry · AdvGen</div>
          </div>
          <div className="p-3.5 rounded-xl bg-slate-900/50 border border-slate-800/80 space-y-1">
            <div className="text-[11px] font-mono uppercase text-slate-400">Patcher Architecture</div>
            <div className="text-base font-bold font-mono text-cyan-400">In-Game Mod JAR</div>
            <div className="text-[11px] text-slate-400">Zero manual jar patching needed</div>
          </div>
        </div>

        {/* Tab Content */}
        {activeTab === 'analyzer' && (
          <div className="space-y-8">
            <ModSelector
              selectedMod={selectedMod}
              onSelectMod={setSelectedMod}
              customMods={customMods}
              onAddCustomMod={handleAddCustomMod}
            />

            <CompatibilityDashboard
              mod={selectedMod}
              onNavigateToSimulator={() => setActiveTab('sandbox')}
              onNavigateToSource={() => setActiveTab('source')}
            />
          </div>
        )}

        {activeTab === 'sandbox' && (
          <InteractiveSimulator currentMod={selectedMod} />
        )}

        {activeTab === 'source' && (
          <CodeViewer />
        )}
      </main>

      {/* Clean Footer */}
      <footer className="border-t border-slate-800/80 bg-[#0B0F17] py-6 px-6 mt-auto">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-400">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-slate-300 font-display">Forge Stuck!!</span>
            <span>·</span>
            <span>NeoForge 1.21.1 Runtime Mod Bridge</span>
            <span>·</span>
            <span>LGPL-3.0 License</span>
          </div>

          <div className="flex items-center gap-4">
            <button
              onClick={() => setActiveTab('analyzer')}
              className="hover:text-slate-200 transition-colors"
            >
              Analyzer
            </button>
            <button
              onClick={() => setActiveTab('sandbox')}
              className="hover:text-slate-200 transition-colors"
            >
              Live Sandbox
            </button>
            <button
              onClick={() => setActiveTab('source')}
              className="hover:text-slate-200 transition-colors"
            >
              Java Source
            </button>
            <button
              onClick={() => setIsInstallGuideOpen(true)}
              className="hover:text-slate-200 transition-colors text-amber-400 font-medium"
            >
              Install Guide
            </button>
          </div>
        </div>
      </footer>

      {/* Installation Guide Modal */}
      <InstallGuideModal
        isOpen={isInstallGuideOpen}
        onClose={() => setIsInstallGuideOpen(false)}
      />
    </div>
  );
}
