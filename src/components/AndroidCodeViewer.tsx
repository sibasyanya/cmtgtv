import React, { useState } from 'react';
import { FileCode, Layers, Shield, Cpu, Copy, Check, Terminal } from 'lucide-react';

interface AndroidCodeViewerProps {
  files: {
    name: string;
    path: string;
    language: string;
    description: string;
    content: string;
  }[];
}

export const AndroidCodeViewer: React.FC<AndroidCodeViewerProps> = ({ files }) => {
  const [selectedFileIdx, setSelectedFileIdx] = useState(0);
  const [copied, setCopied] = useState(false);

  const activeFile = files[selectedFileIdx];

  const handleCopy = () => {
    navigator.clipboard.writeText(activeFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div
      id="android-code-viewer"
      className="flex flex-col h-full bg-zinc-950 text-zinc-100 rounded-2xl border border-zinc-800 overflow-hidden shadow-2xl"
    >
      {/* Top Architecture Bar */}
      <div className="flex flex-wrap items-center justify-between px-6 py-4 bg-zinc-900/90 border-b border-zinc-800">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center">
            <Cpu className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-white tracking-tight">
              Android TV Clean Architecture & TDLib JNI Core
            </h2>
            <p className="text-xs text-zinc-400">
              Kotlin 2.0+ • Target SDK 35 • Leanback 10-Foot UI • Media3 1.11.0
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 mt-2 sm:mt-0">
          <span className="px-2.5 py-1 rounded-md bg-zinc-800 text-[11px] font-mono text-zinc-300 border border-zinc-700">
            ABI: arm64-v8a / armeabi-v7a
          </span>
          <span className="px-2.5 py-1 rounded-md bg-emerald-500/10 text-emerald-400 text-[11px] font-mono border border-emerald-500/30">
            JNI Bridge: libtdjni.so
          </span>
        </div>
      </div>

      {/* Main Split: File Navigator Tabs + Code Editor */}
      <div className="flex flex-col md:flex-row flex-1 overflow-hidden">
        {/* Left Sidebar: File List */}
        <div className="w-full md:w-72 bg-zinc-900/60 border-r border-zinc-800 p-3 overflow-y-auto space-y-1">
          <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-400 px-2 py-1 block">
            Исходные файлы проекта (Android)
          </span>
          {files.map((file, idx) => {
            const isSelected = selectedFileIdx === idx;
            return (
              <button
                key={file.path}
                type="button"
                onClick={() => setSelectedFileIdx(idx)}
                className={`w-full text-left px-3 py-2.5 rounded-xl text-xs font-mono transition flex items-center justify-between ${
                  isSelected
                    ? 'bg-sky-500/20 text-sky-400 border border-sky-500/40 font-semibold'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/50'
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <FileCode className="w-4 h-4 flex-shrink-0" />
                  <span className="truncate">{file.name}</span>
                </div>
              </button>
            );
          })}
        </div>

        {/* Right Pane: Code Viewer */}
        <div className="flex-1 flex flex-col overflow-hidden bg-zinc-950">
          {/* File Header Bar */}
          <div className="flex items-center justify-between px-5 py-3 bg-zinc-900/40 border-b border-zinc-800/80">
            <div>
              <span className="text-xs font-mono text-zinc-300 font-semibold block">
                {activeFile.path}
              </span>
              <span className="text-[11px] text-zinc-400">
                {activeFile.description}
              </span>
            </div>

            <button
              type="button"
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-mono transition"
            >
              {copied ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                  <span className="text-emerald-400">Скопировано</span>
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5" />
                  <span>Копировать</span>
                </>
              )}
            </button>
          </div>

          {/* Code Content */}
          <div className="flex-1 overflow-auto p-5 font-mono text-xs text-zinc-300 leading-relaxed bg-[#0d1117]">
            <pre className="whitespace-pre">
              <code>{activeFile.content}</code>
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
};
