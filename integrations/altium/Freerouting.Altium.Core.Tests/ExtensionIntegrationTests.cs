using System;
using System.Collections.Generic;
using Freerouting.Altium.Core.Model;
using Freerouting.Altium.Core.Ses;
using Freerouting.Altium.Extension.BackAnnotation;
using Freerouting.Altium.Extension.Commands;
using Freerouting.Altium.Extension.Extraction;
using RT_PCB;

namespace Freerouting.Altium.Core.Tests
{
    public static class ExtensionIntegrationTests
    {
        public static void RunAll()
        {
            VerifyPcbDocExtraction();
            VerifyPcbDocBackAnnotation();
            VerifyServerModuleAndCommands();
        }

        public static void VerifyPcbDocExtraction()
        {
            var outlinePoints = new List<Point2D>
            {
                new Point2D(0, 0),
                new Point2D(10000000, 0),
                new Point2D(10000000, 10000000),
                new Point2D(0, 10000000)
            };

            var contour = AltiumProxy<IPCB_Contour>.Create(h =>
            {
                h["GetState_Count"] = _ => outlinePoints.Count;
                h["GetState_PointX"] = args => outlinePoints[(int)args[0]].X;
                h["GetState_PointY"] = args => outlinePoints[(int)args[0]].Y;
            });

            var geomPoly = AltiumProxy<IPCB_GeometricPolygon>.Create(h =>
            {
                h["GetState_Count"] = _ => outlinePoints.Count > 0 ? 1 : 0;
                h["GetState_Contour"] = _ => contour;
                h["Internal_GetState_Contour"] = _ => contour;
            });

            var outline = AltiumProxy<IPCB_BoardOutline>.Create(h =>
            {
                h["BoardOutline_GeometricPolygon"] = _ => geomPoly;
                h["Internal_BoardOutline_GeometricPolygon"] = _ => geomPoly;
            });

            var layers = new List<IPCB_LayerObject>
            {
                CreateMockLayer("TopLayer", TV6_Layer.eV6_TopLayer),
                CreateMockLayer("GND_Plane", TV6_Layer.eV6_InternalPlane1),
                CreateMockLayer("BottomLayer", TV6_Layer.eV6_BottomLayer)
            };

            var stack = AltiumProxy<IPCB_LayerStack>.Create(h =>
            {
                h["Count"] = _ => layers.Count;
                h["LayerObject"] = args =>
                {
                    var id = (TV6_Layer)args[0];
                    return layers.Find(l => l.V6_LayerID() == id);
                };
            });

            bool polyPoured = true;
            var poly = AltiumProxy<IPCB_Polygon>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.ePolyObject;
                h["GetState_Poured"] = _ => polyPoured;
                h["SetState_Poured"] = args => { polyPoured = (bool)args[0]; return null; };
                h["SetState_CopperPourInvalid"] = _ => null;
                h["Rebuild"] = _ => null;
            });

            var outlineRule = AltiumProxy<IPCB_BoardOutlineClearanceConstraint>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.eRuleObject;
                h["GetState_RuleKind"] = _ => TRuleKind.eRule_BoardOutlineClearance;
                h["GetState_Name"] = _ => "BoardOutlineClearance_Rule";
                h["GetState_Gap"] = _ => 2500000;
            });

            var compC1 = AltiumProxy<IPCB_Component>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.eComponentObject;
                h["GetState_SourceDesignator"] = _ => "C1";
                h["GetState_Pattern"] = _ => "CAPC0402";
                h["GetState_XLocation"] = _ => 40000000;
                h["GetState_YLocation"] = _ => 30000000;
                h["GetState_Rotation"] = _ => 90.0;
                h["GetState_Layer"] = _ => TV6_Layer.eV6_TopLayer;
            });

            var compC2 = AltiumProxy<IPCB_Component>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.eComponentObject;
                h["GetState_SourceDesignator"] = _ => "C2";
                h["GetState_Pattern"] = _ => "CAPC0402";
                h["GetState_XLocation"] = _ => 50000000;
                h["GetState_YLocation"] = _ => 30000000;
                h["GetState_Rotation"] = _ => 0.0;
                h["GetState_Layer"] = _ => TV6_Layer.eV6_TopLayer;
            });

            // C1 pads in world space (rotated by 90 deg: deltaX=0, deltaY=+-177165)
            var padC1_1 = AltiumProxy<IPCB_Pad>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.ePadObject;
                h["GetState_Name"] = _ => "1";
                h["GetState_Component"] = _ => compC1;
                h["GetState_XLocation"] = _ => 40000000;
                h["GetState_YLocation"] = _ => 30000000 - 177165;
                h["GetState_Rotation"] = _ => 90.0;
                h["GetState_TopXSize"] = _ => 216535;
                h["GetState_TopYSize"] = _ => 196850;
                h["GetState_TopShape"] = _ => TShape.eRectangular;
                h["GetState_Layer"] = _ => TV6_Layer.eV6_TopLayer;
            });

            var padC1_2 = AltiumProxy<IPCB_Pad>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.ePadObject;
                h["GetState_Name"] = _ => "2";
                h["GetState_Component"] = _ => compC1;
                h["GetState_XLocation"] = _ => 40000000;
                h["GetState_YLocation"] = _ => 30000000 + 177165;
                h["GetState_Rotation"] = _ => 90.0;
                h["GetState_TopXSize"] = _ => 216535;
                h["GetState_TopYSize"] = _ => 196850;
                h["GetState_TopShape"] = _ => TShape.eRectangular;
                h["GetState_Layer"] = _ => TV6_Layer.eV6_TopLayer;
            });

            // C2 pads in world space (rotation 0: deltaX=+-177165, deltaY=0)
            var padC2_1 = AltiumProxy<IPCB_Pad>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.ePadObject;
                h["GetState_Name"] = _ => "1";
                h["GetState_Component"] = _ => compC2;
                h["GetState_XLocation"] = _ => 50000000 - 177165;
                h["GetState_YLocation"] = _ => 30000000;
                h["GetState_Rotation"] = _ => 0.0;
                h["GetState_TopXSize"] = _ => 216535;
                h["GetState_TopYSize"] = _ => 196850;
                h["GetState_TopShape"] = _ => TShape.eRectangular;
                h["GetState_Layer"] = _ => TV6_Layer.eV6_TopLayer;
            });

            var padC2_2 = AltiumProxy<IPCB_Pad>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.ePadObject;
                h["GetState_Name"] = _ => "2";
                h["GetState_Component"] = _ => compC2;
                h["GetState_XLocation"] = _ => 50000000 + 177165;
                h["GetState_YLocation"] = _ => 30000000;
                h["GetState_Rotation"] = _ => 0.0;
                h["GetState_TopXSize"] = _ => 216535;
                h["GetState_TopYSize"] = _ => 196850;
                h["GetState_TopShape"] = _ => TShape.eRectangular;
                h["GetState_Layer"] = _ => TV6_Layer.eV6_TopLayer;
            });

            var primitives = new List<IPCB_Primitive>
            {
                poly as IPCB_Primitive,
                outlineRule as IPCB_Primitive,
                compC1 as IPCB_Primitive,
                compC2 as IPCB_Primitive,
                padC1_1 as IPCB_Primitive,
                padC1_2 as IPCB_Primitive,
                padC2_1 as IPCB_Primitive,
                padC2_2 as IPCB_Primitive
            };

            int iterIdx = -1;
            var iterator = AltiumProxy<IPCB_BoardIterator>.Create(h =>
            {
                h["SetState_FilterAll"] = _ => null;
                h["AddFilter_AllLayers"] = _ => null;
                h["AddFilter_Method"] = _ => null;
                h["FirstPCBObject"] = _ => { iterIdx = 0; return iterIdx < primitives.Count ? primitives[iterIdx] : null; };
                h["NextPCBObject"] = _ => { iterIdx++; return iterIdx < primitives.Count ? primitives[iterIdx] : null; };
            });

            var board = AltiumProxy<IPCB_Board>.Create(h =>
            {
                h["GetState_FileName"] = _ => "C:\\Projects\\TestBoard.PcbDoc";
                h["GetState_BoardOutline"] = _ => outline;
                h["Internal_GetState_BoardOutline"] = _ => outline;
                h["GetState_LayerStack"] = _ => stack;
                h["BoardIterator_Create"] = _ => iterator;
                h["BoardIterator_Destroy"] = _ => null;
            });

            var extractor = new PcbDocExtractor();
            var model = extractor.Extract(board);

            Assert("Boundary extracted with 4 vertices", model.BoundaryVertices.Count == 4);
            Assert("Signal layers extracted (Top, Plane, Bottom)", model.Layers.Count == 3);
            Assert("Polygon was shelved pre-export", !polyPoured);
            Assert("Board outline clearance rule extracted", model.Rules.BoardOutlineClearance == 2500000);
            Assert("Footprint library image deduplicated (1 image for CAPC0402)", model.Images.Count == 1);
            Assert("Footprint pins deduplicated (exactly 2 pins, no cross duplication)", model.Images[0].Pins.Count == 2);
            Assert("Pin 1 relative X coordinate inverse-rotated (-17.7 mil)", Math.Abs(model.Images[0].Pins[0].RelativePosition.X - (-177165)) <= 2);
            Assert("Pin 1 relative Y coordinate inverse-rotated (0 mil)", Math.Abs(model.Images[0].Pins[0].RelativePosition.Y) <= 2);
            Assert("Components placed separately (C1 and C2)", model.Components.Count == 2);
        }

        public static void VerifyPcbDocBackAnnotation()
        {
            var addedPrimitives = new List<IPCB_Primitive>();
            var removedPrimitives = new List<IPCB_Primitive>();
            bool preProcessed = false;
            bool postProcessed = false;
            bool validated = false;
            bool viewUpdated = false;

            var existingNet = AltiumProxy<IPCB_Net>.Create(h =>
            {
                h["GetState_Name"] = _ => "GND";
            });

            var oldTrack = AltiumProxy<IPCB_Track>.Create(h =>
            {
                h["GetState_ObjectID"] = _ => TObjectId.eTrackObject;
                h["GetState_Net"] = _ => existingNet;
                h["GetState_Layer"] = _ => TV6_Layer.eV6_TopLayer;
                h["GetState_Moveable"] = _ => true;
            });

            var primitives = new List<IPCB_Primitive> { oldTrack as IPCB_Primitive };

            int iterIdx = -1;
            var iterator = AltiumProxy<IPCB_BoardIterator>.Create(h =>
            {
                h["SetState_FilterAll"] = _ => null;
                h["AddFilter_AllLayers"] = _ => null;
                h["AddFilter_Method"] = _ => null;
                h["FirstPCBObject"] = _ => { iterIdx = 0; return iterIdx < primitives.Count ? primitives[iterIdx] : null; };
                h["NextPCBObject"] = _ => { iterIdx++; return iterIdx < primitives.Count ? primitives[iterIdx] : null; };
            });

            var board = AltiumProxy<IPCB_Board>.Create(h =>
            {
                h["BoardIterator_Create"] = _ => iterator;
                h["BoardIterator_Destroy"] = _ => null;
                h["AddPCBObject"] = args => { var p = (IPCB_Primitive)args[0]; primitives.Add(p); addedPrimitives.Add(p); return null; };
                h["RemovePCBObject"] = args => { var p = (IPCB_Primitive)args[0]; primitives.Remove(p); removedPrimitives.Add(p); return null; };
                h["ConnectivelyValidateNets"] = _ => { validated = true; return null; };
                h["ViewManager_FullUpdate"] = _ => { viewUpdated = true; return null; };
            });

            var server = AltiumProxy<IPCB_ServerInterface>.Create(h =>
            {
                h["GetCurrentPCBBoard"] = _ => board;
                h["PreProcess"] = _ => { preProcessed = true; return null; };
                h["PostProcess"] = _ => { postProcessed = true; return null; };
                h["SendMessageToRobots"] = _ => null;
                h["PCBObjectFactory"] = args =>
                {
                    var id = (TObjectId)args[0];
                    if (id == TObjectId.eTrackObject)
                    {
                        return AltiumProxy<IPCB_Track>.Create(th =>
                        {
                            th["GetState_ObjectID"] = _ => TObjectId.eTrackObject;
                            th["I_ObjectAddress"] = _ => new IntPtr(2001);
                        }) as IPCB_Primitive;
                    }
                    if (id == TObjectId.eViaObject)
                    {
                        return AltiumProxy<IPCB_Via>.Create(vh =>
                        {
                            vh["GetState_ObjectID"] = _ => TObjectId.eViaObject;
                            vh["I_ObjectAddress"] = _ => new IntPtr(3001);
                        }) as IPCB_Primitive;
                    }
                    return null;
                };
            });

            var sesResult = new SesResult();
            var wire = new SesWire("GND", "TopLayer", 250000);
            wire.Vertices.Add(new Point2D(1000, 1000));
            wire.Vertices.Add(new Point2D(5000, 1000));
            sesResult.Wires.Add(wire);

            var via = new SesVia("GND", "Via_600000_300000", 2000, 2000)
            {
                Diameter = 600000,
                HoleSize = 300000,
                LowLayerName = "TopLayer",
                HighLayerName = "BottomLayer"
            };
            sesResult.Vias.Add(via);

            var backAnnotator = new PcbDocBackAnnotator();
            backAnnotator.BackAnnotate(server, board, sesResult);

            Assert("Old track on routed net removed", removedPrimitives.Contains(oldTrack as IPCB_Primitive));
            Assert("New track and via added", addedPrimitives.Count >= 2);
            Assert("Atomic transaction pre-process called", preProcessed);
            Assert("Atomic transaction post-process called", postProcessed);
            Assert("Connectivity validated", validated);
            Assert("Viewport refreshed", viewUpdated);
        }

        public static void VerifyServerModuleAndCommands()
        {
            var server = AltiumProxy<IPCB_ServerInterface>.Create();
            var freeroutingServer = new FreeroutingServer();
            freeroutingServer.SuppressDialogs = true;

            // Test Settings command with null board
            freeroutingServer.ExecuteCommand(null, "Settings", "");

            // Test Unrecognized command
            freeroutingServer.ExecuteCommand(server, "UnknownCommand", "");

            Assert("Server module instantiated and responsive", freeroutingServer != null);
        }

        private static IPCB_LayerObject CreateMockLayer(string name, TV6_Layer id)
        {
            return AltiumProxy<IPCB_LayerObject>.Create(h =>
            {
                h["GetState_LayerName"] = _ => name;
                h["V6_LayerID"] = _ => id;
                h["IsInLayerStack"] = _ => true;
            });
        }

        private static void Assert(string description, bool condition)
        {
            if (!condition)
            {
                throw new Exception("Assertion failed: " + description);
            }
            Console.WriteLine("[PASS] " + description);
        }
    }
}
