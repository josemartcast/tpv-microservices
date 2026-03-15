using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text;

internal static class Program
{
    private static readonly byte[] Marker = Encoding.ASCII.GetBytes("TPVBUNDL");

    public static int Main(string[] args)
    {
        try
        {
            string exePath = Process.GetCurrentProcess().MainModule.FileName;
            string tempRoot = Path.Combine(Path.GetTempPath(), "tpv-bar-bootstrap", Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(tempRoot);

            string bundleZip = Path.Combine(tempRoot, "bundle.zip");
            ExtractEmbeddedPayload(exePath, bundleZip);
            ZipFile.ExtractToDirectory(bundleZip, tempRoot);

            string launcher = Path.Combine(tempRoot, "setup-launcher.cmd");
            if (!File.Exists(launcher))
            {
                Console.Error.WriteLine("No se encontro setup-launcher.cmd en el bundle extraido.");
                return 1;
            }

            var process = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = launcher,
                    WorkingDirectory = tempRoot,
                    UseShellExecute = true
                }
            };

            process.Start();
            process.WaitForExit();
            return process.ExitCode;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("Fallo iniciando TPV Bar Setup: " + ex.Message);
            return 1;
        }
    }

    private static void ExtractEmbeddedPayload(string exePath, string destinationZip)
    {
        using (var stream = new FileStream(exePath, FileMode.Open, FileAccess.Read, FileShare.Read))
        {
            if (stream.Length < 16)
            {
                throw new InvalidOperationException("Ejecutable invalido: cabecera de payload ausente.");
            }

            stream.Seek(-16, SeekOrigin.End);
            byte[] footer = new byte[16];
            int read = stream.Read(footer, 0, footer.Length);
            if (read != footer.Length)
            {
                throw new InvalidOperationException("Ejecutable invalido: no se pudo leer footer.");
            }

            long payloadLength = BitConverter.ToInt64(footer, 0);
            if (!MatchesMarker(footer, 8))
            {
                throw new InvalidOperationException("Ejecutable invalido: marker no encontrado.");
            }

            long payloadOffset = stream.Length - 16 - payloadLength;
            if (payloadOffset < 0)
            {
                throw new InvalidOperationException("Ejecutable invalido: longitud de payload incorrecta.");
            }

            stream.Seek(payloadOffset, SeekOrigin.Begin);
            using (var output = new FileStream(destinationZip, FileMode.Create, FileAccess.Write, FileShare.None))
            {
                stream.CopyTo(output, 1024 * 1024);
                output.SetLength(payloadLength);
            }
        }
    }

    private static bool MatchesMarker(byte[] buffer, int offset)
    {
        if (buffer.Length < offset + Marker.Length)
        {
            return false;
        }

        for (int i = 0; i < Marker.Length; i++)
        {
            if (buffer[offset + i] != Marker[i])
            {
                return false;
            }
        }

        return true;
    }
}
