import { useState } from 'react';
import { Download, BookOpen, CheckCircle2 } from 'lucide-react';
import { downloadForgeStuckJar, MOD_JAR_FILENAME, MOD_VERSION } from '../utils/zipExporter';

interface HeaderProps {
  activeTab: 'analyzer' | 'sandbox' | 'source' | 'install';
  setActiveTab: (tab: 'analyzer' | 'sandbox' | 'source' | 'install') => void;
  onOpenInstallGuide: () => void;
}

export function Header({ activeTab, setActiveTab, onOpenInstallGuide }: HeaderProps) {
  const [downloading, setDownloading] = useState(false);
  const [downloadSuccess, setDownloadSuccess] = useState(false);

  const handleDownload = async () => {
    try {
      setDownloading(true);
      await downloadForgeStuckJar();
      setDownloadSuccess(true);
      setTimeout(() => setDownloadSuccess(false), 3000);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <header className="sticky top-0 z-30 flex items-center justify-between gap-8 px-6 py-3.5 border-b border-slate-800 bg-[#0B0F17]/95 backdrop-blur-md">
      {/* Zone 1: Single text element wordmark */}
      <div className="flex items-center gap-3 shrink-0">
        <a 
          href="#home" 
          onClick={(e) => { e.preventDefault(); setActiveTab('analyzer'); }}
          className="text-lg font-bold tracking-tight text-white flex items-center gap-2 whitespace-nowrap"
        >
          <span className="w-2.5 h-2.5 rounded-sm bg-amber-400 rotate-45 inline-block" />
          <span className="font-display">Forge Stuck!!</span>
          <span className="text-xs text-amber-400 font-mono font-normal">v{MOD_VERSION}</span>
        </a>
      </div>

      {/* Zone 2: 4-5 clean single-line text navigation links */}
      <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-400">
        <button
          onClick={() => setActiveTab('analyzer')}
          className={`transition-colors whitespace-nowrap shrink-0 hover:text-white ${
            activeTab === 'analyzer' ? 'text-amber-400 font-semibold' : ''
          }`}
        >
          Mod Analyzer
        </button>
        <button
          onClick={() => setActiveTab('sandbox')}
          className={`transition-colors whitespace-nowrap shrink-0 hover:text-white ${
            activeTab === 'sandbox' ? 'text-amber-400 font-semibold' : ''
          }`}
        >
          Live Shim Sandbox
        </button>
        <button
          onClick={() => setActiveTab('source')}
          className={`transition-colors whitespace-nowrap shrink-0 hover:text-white ${
            activeTab === 'source' ? 'text-amber-400 font-semibold' : ''
          }`}
        >
          Java Mod Source
        </button>
        <button
          onClick={onOpenInstallGuide}
          className="transition-colors whitespace-nowrap shrink-0 hover:text-white flex items-center gap-1.5"
        >
          <BookOpen className="w-3.5 h-3.5" />
          <span>Installation Guide</span>
        </button>
      </nav>

      {/* Zone 3: 1 primary action */}
      <div className="flex items-center gap-3 shrink-0">
        <button
          onClick={handleDownload}
          disabled={downloading}
          className="flex items-center gap-2 px-4 py-2 text-xs font-semibold text-slate-950 bg-amber-400 hover:bg-amber-300 rounded-lg transition-all whitespace-nowrap shrink-0 shadow-sm disabled:opacity-50"
        >
          {downloadSuccess ? (
            <>
              <CheckCircle2 className="w-4 h-4 text-emerald-950" />
              <span>Downloaded {MOD_JAR_FILENAME}!</span>
            </>
          ) : downloading ? (
            <>
              <span className="w-3.5 h-3.5 border-2 border-slate-900 border-t-transparent rounded-full animate-spin" />
              <span>Packaging v{MOD_VERSION}...</span>
            </>
          ) : (
            <>
              <Download className="w-4 h-4" />
              <span>Download {MOD_JAR_FILENAME}</span>
            </>
          )}
        </button>
      </div>
    </header>
  );
}
