import { ShieldCheck, Layers, AlertCircle, Wrench, ArrowRight, Zap, Check } from 'lucide-react';
import { ModProfile } from '../data/modProfiles';

interface CompatibilityDashboardProps {
  mod: ModProfile;
  onNavigateToSimulator: () => void;
  onNavigateToSource: () => void;
}

export function CompatibilityDashboard({ mod, onNavigateToSimulator, onNavigateToSource }: CompatibilityDashboardProps) {
  return (
    <div className="space-y-6">
      {/* Overview & Verdict Banner */}
      <div className="p-5 rounded-2xl bg-slate-900/60 border border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2 text-xs text-slate-400">
            <span>{mod.name}</span>
            <span>·</span>
            <span className="font-mono">{mod.jarName}</span>
            <span>·</span>
            <span>{mod.jarSize}</span>
            <span>·</span>
            <span>{mod.classCount} classes</span>
          </div>
          <h1 className="text-xl md:text-2xl font-display font-bold text-white flex items-center gap-2">
            <span>{mod.verdict}</span>
          </h1>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            By <span className="text-slate-300 font-medium">{mod.authors}</span>. Forge Stuck!! will intercept classloading in NeoForge 1.21.1 and dynamically resolve all {mod.hooks.length} breaking API differences.
          </p>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          <div className="px-4 py-3 rounded-xl bg-slate-950/80 border border-slate-800/80 text-center">
            <div className="text-2xl font-bold font-mono text-amber-400 tabular-nums">
              {mod.compatibilityScore}%
            </div>
            <div className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">
              Compatibility
            </div>
          </div>
          <div className="px-4 py-3 rounded-xl bg-slate-950/80 border border-slate-800/80 text-center">
            <div className="text-2xl font-bold font-mono text-emerald-400 tabular-nums">
              {mod.hooks.length}
            </div>
            <div className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">
              Shims Engaged
            </div>
          </div>
        </div>
      </div>

      {/* Active Runtime Shims in Forge Stuck!! */}
      <div className="p-5 rounded-2xl bg-slate-900/40 border border-slate-800 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Layers className="w-4 h-4 text-amber-400" />
            <h3 className="text-sm font-semibold text-white font-display">
              Active Runtime Shims Engaged in In-Game JAR
            </h3>
          </div>
          <button
            onClick={onNavigateToSource}
            className="text-xs text-amber-400 hover:text-amber-300 transition-colors flex items-center gap-1 font-medium"
          >
            <span>View Java Source</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
          {mod.activeAdapters.map((adapter, idx) => (
            <div
              key={idx}
              className="p-3 rounded-lg bg-slate-950/60 border border-slate-800/80 flex items-start gap-2.5 text-xs"
            >
              <Check className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
              <div>
                <span className="font-mono text-slate-200 font-medium block">
                  {adapter}
                </span>
                <span className="text-[11px] text-slate-500 mt-0.5 block">
                  Autonomously patches calls at classload time inside NeoForge 1.21.1.
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Breaking 1.20.1 to 1.21.1 API Hook Diagnostic Table */}
      <div className="p-5 rounded-2xl bg-slate-900/40 border border-slate-800 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h3 className="text-sm font-semibold text-white font-display flex items-center gap-2">
              <Wrench className="w-4 h-4 text-amber-400" />
              <span>Bytecode Analysis: Breaking API Usage & Shims</span>
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Exact method calls in {mod.name} rewritten or emulated at runtime by Forge Stuck!!
            </p>
          </div>
          <button
            onClick={onNavigateToSimulator}
            className="text-xs px-3 py-1.5 rounded-lg bg-amber-400/10 hover:bg-amber-400/20 text-amber-300 border border-amber-400/30 transition-colors flex items-center gap-1.5 font-medium shrink-0"
          >
            <Zap className="w-3.5 h-3.5 text-amber-400" />
            <span>Test in Live Sandbox</span>
          </button>
        </div>

        <div className="overflow-x-auto border border-slate-800 rounded-xl">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950/80 text-slate-400 font-medium border-b border-slate-800">
              <tr>
                <th className="py-2.5 px-3.5">Category</th>
                <th className="py-2.5 px-3.5">1.20.1 Forge Call</th>
                <th className="py-2.5 px-3.5">1.21.1 NeoForge Target</th>
                <th className="py-2.5 px-3.5">Forge Stuck!! Shim</th>
                <th className="py-2.5 px-3.5 text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/80 bg-slate-900/20">
              {mod.hooks.map((hook, idx) => (
                <tr key={idx} className="hover:bg-slate-800/30 transition-colors">
                  <td className="py-3 px-3.5 whitespace-nowrap">
                    <span className="font-mono text-[11px] text-slate-300">
                      {hook.sourceType}
                    </span>
                  </td>
                  <td className="py-3 px-3.5 font-mono text-rose-300/90 whitespace-nowrap">
                    {hook.legacy1201}
                  </td>
                  <td className="py-3 px-3.5 font-mono text-cyan-300/90 whitespace-nowrap">
                    {hook.neoForge1211}
                  </td>
                  <td className="py-3 px-3.5 font-mono text-amber-300/90 whitespace-nowrap">
                    {hook.activeShim}
                  </td>
                  <td className="py-3 px-3.5 text-right whitespace-nowrap">
                    <span className="text-[11px] font-medium text-emerald-400">
                      {hook.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Target Mod Compatibility Highlights & Notes */}
      <div className="p-5 rounded-2xl bg-slate-900/40 border border-slate-800 space-y-3">
        <h3 className="text-sm font-semibold text-white font-display flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <span>Behavioral Invariants & In-Game Verification Notes</span>
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {mod.notes.map((note, idx) => (
            <div key={idx} className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/60 text-xs text-slate-300 leading-relaxed">
              <span className="text-amber-400 font-semibold block mb-1">Checkpoint 0{idx + 1}</span>
              {note}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
