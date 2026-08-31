'use client';

import React, { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { motion, useAnimation } from 'framer-motion';
import { Search, ArrowLeft } from 'lucide-react';

export default function NotFound() {
  const containerRef = useRef<HTMLDivElement>(null);
  const [mousePos, setMousePos] = useState({ x: -1000, y: -1000 });
  const [isTouch, setIsTouch] = useState(false);

  useEffect(() => {
    // Detect touch device
    if (window.matchMedia("(pointer: coarse)").matches) {
      setIsTouch(true);
      // Auto-sweep animation for touch devices
      let angle = 0;
      const interval = setInterval(() => {
        if (!containerRef.current) return;
        const rect = containerRef.current.getBoundingClientRect();
        const centerX = rect.width / 2;
        const centerY = rect.height / 2;
        const radius = Math.min(rect.width, rect.height) * 0.3;
        
        angle += 0.05;
        setMousePos({
          x: centerX + Math.cos(angle) * radius,
          y: centerY + Math.sin(angle * 2) * (radius * 0.5) // figure 8 pattern
        });
      }, 50);
      return () => clearInterval(interval);
    }
  }, []);

  const handleMouseMove = (e: React.MouseEvent) => {
    if (isTouch) return;
    if (containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      setMousePos({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top
      });
    }
  };

  return (
    <div 
      ref={containerRef}
      onMouseMove={handleMouseMove}
      className="relative w-full h-screen bg-[#FAF7F2] overflow-hidden flex flex-col items-center justify-center cursor-none"
    >
      {/* Base Layer: Giant low-contrast 404 */}
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
        <h1 className="text-[30vw] font-black text-stone-200/50 leading-none select-none">
          404
        </h1>
      </div>

      {/* Hidden Text Revealed by Lens */}
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-10"
           style={{
             clipPath: `circle(80px at ${mousePos.x}px ${mousePos.y}px)`,
             WebkitClipPath: `circle(80px at ${mousePos.x}px ${mousePos.y}px)`,
             backdropFilter: 'brightness(1.1) contrast(1.2)'
           }}
      >
        <div className="absolute inset-0 bg-[#F15927]/10" />
        <p className="text-2xl md:text-4xl font-black text-[#F15927] tracking-tight">
          Looks like this dish got eaten... <span className="inline-block scale-150 ml-2">🔎</span>
        </p>
      </div>

      {/* Magnifier Glass SVG trailing the cursor */}
      <motion.div 
        className="absolute z-20 pointer-events-none flex items-center justify-center mix-blend-difference text-[#F15927]"
        animate={{
          x: mousePos.x - 24,
          y: mousePos.y - 24,
        }}
        transition={{
          type: 'spring',
          damping: 30,
          stiffness: 400,
          mass: 0.5
        }}
      >
        <Search className="w-12 h-12 opacity-80" />
      </motion.div>

      {/* Standard UI controls */}
      <div className="relative z-30 mt-64">
        <Link 
          href="/menu" 
          className="bg-stone-900 hover:bg-stone-800 text-white font-bold px-6 py-3 rounded-full flex items-center gap-2 transition-transform hover:scale-105 active:scale-95 shadow-xl"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to Menu
        </Link>
      </div>
    </div>
  );
}
