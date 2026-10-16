import React, { useRef, useState } from 'react';
import { UploadCloud, CheckCircle2, Flame, Dna, Cpu, FileArchive } from 'lucide-react';
import { ModProfile, TARGET_MOD_PROFILES } from '../data/modProfiles';
import { analyzeJarFile } from '../utils/jarAnalyzer';

interface ModSelectorProps {
  selectedMod: ModProfile;
  onSelectMod: (mod: ModProfile) => void;
  customMods: ModProfile[];
  onAddCustomMod: (mod: ModProfile) => void;
}

export function ModSelector({ selectedMod, onSelectMod, customMods, onAddCustomMod }: ModSelectorProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileUpload = async (file: File) => {
    if (!file.name.toLowerCase().endsWith('.jar')) {
      setErrorMsg('Please upload a valid Minecraft mod JAR file (.jar)');
      setTimeout(() => setErrorMsg(null), 4000);
      return;
    }

    try {
      setAnalyzing(true);
      setErrorMsg(null);
      const analyzedProfile = await analyzeJarFile(file);
      onAddCustomMod(analyzedProfile);
      onSelectMod(analyzedProfile);
    } catch {
      setErrorMsg('Failed to read JAR bytecode. Ensure it is a valid ZIP/JAR archive.');
      setTimeout(() => setErrorMsg(null), 4000);
    } finally {
      setAnalyzing(false);
    }
  };

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFileUpload(e.dataTransfer.files[0]);
    }
  };

  const getIcon = (id: string) => {
    if (id === 'thermal') return <Flame className="w-4 h-4 text-amber-400" />;
    if (id === 'gendustry') return <Dna className="w-4 h-4 text-emerald-400" />;
    if (id === 'advgenerators') return <Cpu className="w-4 h-4 text-cyan-400" />;
    return <FileArchive className="w-4 h-4 text-purple-400" />;
  };

  const allMods = [...TARGET_MOD_PROFILES, ...customMods];

  return (
    <div className="space-y-4">
      {/* Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold tracking-wide uppercase text-slate-400">
            Select 1.20.1 Mod to Analyze
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Check compatibility and active in-game shims for Thermal Series, Gendustry CE, Advanced Generators, or any custom JAR.
          </p>
        </div>
        <div className="text-xs text-slate-400 font-mono">
          Loaded Profiles: <span className="text-white font-medium">{allMods.length}</span>
        </div>
      </div>

      {/* Preset Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        {TARGET_MOD_PROFILES.map((mod) => {
          const isSelected = selectedMod.id === mod.id;
          return (
            <button
              key={mod.id}
              onClick={() => onSelectMod(mod)}
              className={`text-left p-4 rounded-xl border transition-all relative group flex flex-col justify-between ${
                isSelected
                  ? 'bg-slate-900/90 border-amber-500/60 shadow-lg shadow-amber-500/5 ring-1 ring-amber-500/30'
                  : 'bg-slate-900/40 border-slate-800 hover:border-slate-700 hover:bg-slate-900/60'
              }`}
            >
              <div>
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="p-1.5 rounded-lg bg-slate-800/80 border border-slate-700/60">
                      {getIcon(mod.id)}
                    </span>
                    <span className="text-xs font-semibold text-slate-400 font-mono">
                      {mod.modid}
                    </span>
                  </div>
                  {isSelected && (
                    <span className="text-[11px] font-medium text-amber-400 flex items-center gap-1">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Active
                    </span>
                  )}
                </div>

                <h3 className="font-display font-semibold text-white text-base leading-snug group-hover:text-amber-300 transition-colors">
                  {mod.name}
                </h3>
                <p className="text-xs text-slate-400 mt-1 line-clamp-2 leading-relaxed">
                  {mod.description}
                </p>
              </div>

              <div className="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-500 font-mono">
                <span>v{mod.version}</span>
                <span>{mod.classCount} classes</span>
                <span className="text-emerald-400 font-semibold">{mod.compatibilityScore}%</span>
              </div>
            </button>
          );
        })}
      </div>

      {/* Custom Mods Row if any */}
      {customMods.length > 0 && (
        <div className="pt-2">
          <div className="text-xs font-semibold text-slate-400 mb-2">Custom Uploaded Mods:</div>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            {customMods.map((mod) => (
              <button
                key={mod.id}
                onClick={() => onSelectMod(mod)}
                className={`text-left p-3 rounded-lg border text-xs flex items-center justify-between ${
                  selectedMod.id === mod.id
                    ? 'bg-slate-900 border-purple-500/60 text-white'
                    : 'bg-slate-900/30 border-slate-800 text-slate-300 hover:border-slate-700'
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <FileArchive className="w-4 h-4 text-purple-400 shrink-0" />
                  <span className="truncate font-medium">{mod.name}</span>
                </div>
                <span className="font-mono text-purple-300 ml-2 shrink-0">{mod.compatibilityScore}%</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Drag & Drop Upload Zone */}
      <div
        onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={onDrop}
        onClick={() => fileInputRef.current?.click()}
        className={`p-5 rounded-xl border border-dashed transition-all cursor-pointer text-center ${
          isDragging
            ? 'border-amber-400 bg-amber-500/5'
            : 'border-slate-800 hover:border-slate-700 bg-slate-900/20 hover:bg-slate-900/40'
        }`}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".jar"
          className="hidden"
          onChange={(e) => {
            if (e.target.files && e.target.files[0]) {
              handleFileUpload(e.target.files[0]);
            }
          }}
        />

        <div className="flex flex-col items-center justify-center gap-2">
          {analyzing ? (
            <div className="flex items-center gap-3 py-1">
              <span className="w-4 h-4 border-2 border-amber-400 border-t-transparent rounded-full animate-spin" />
              <span className="text-xs text-amber-300 font-medium">
                Scanning bytecode & detecting 1.20.1 Forge calls...
              </span>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-2 text-xs font-medium text-slate-300">
                <UploadCloud className="w-4 h-4 text-amber-400" />
                <span>Drop any Forge 1.20.1 mod .jar file here to analyze compatibility</span>
              </div>
              <p className="text-[11px] text-slate-500">
                Client-side bytecode inspection · Class constant pool scanning · Live shim resolution
              </p>
            </>
          )}
        </div>

        {errorMsg && (
          <div className="mt-2 text-xs text-rose-400 font-medium">
            {errorMsg}
          </div>
        )}
      </div>
    </div>
  );
}
