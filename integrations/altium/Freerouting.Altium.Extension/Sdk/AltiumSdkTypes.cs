using System;

namespace Freerouting.Altium.Extension.Sdk
{
    public static class PCBMessageConstants
    {
        public static readonly IntPtr c_BroadCast = IntPtr.Zero; // Nil broadcast pointer in Delphi
        public const ushort PCBM_BoardRegisteration = 2;          // Native Altium robot message constant = 2
        public const ushort PCBM_PolyRepour = 101;                // Native polygon repour message constant = 101
    }
}
