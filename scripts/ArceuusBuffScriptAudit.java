import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Read-only native JS5 script decoder, runnable with the installed JDK; no Gradle needed. */
class ArceuusBuffScriptAudit {
    record Instruction(int opcode, Object argument) {}
    record Script(List<Instruction> instructions, List<Map<Integer,Integer>> switches) {}
    static int medium(RandomAccessFile f) throws IOException { return f.readUnsignedByte()<<16 | f.readUnsignedByte()<<8 | f.readUnsignedByte(); }
    static byte[] group(RandomAccessFile index, RandomAccessFile data, int id) throws IOException {
        index.seek(id*6L); int length=medium(index), sector=medium(index);
        if(length==0) return null;
        var out=new ByteArrayOutputStream(); int chunk=0;
        while(out.size()<length) {
            data.seek(sector*520L);
            if(data.readUnsignedShort()!=id || data.readUnsignedShort()!=chunk) throw new IOException("JS5 sector mismatch");
            sector=medium(data);
            if(data.readUnsignedByte()!=12) throw new IOException("JS5 index mismatch");
            byte[] part=new byte[Math.min(512,length-out.size())]; data.readFully(part); out.write(part); chunk++;
        }
        var raw=new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        int compression=raw.readUnsignedByte(), compressed=raw.readInt();
        if(compression==0) return raw.readNBytes(compressed);
        int decodedLength=raw.readInt();
        if(compression!=2) throw new IOException("Unsupported compression "+compression);
        byte[] decoded=new GZIPInputStream(new ByteArrayInputStream(raw.readNBytes(compressed))).readAllBytes();
        if(decoded.length!=decodedLength) throw new IOException("JS5 decompression length");
        return decoded;
    }
    static Script decode(byte[] data) {
        var b=ByteBuffer.wrap(data); int length=b.getShort(data.length-2)&65535;
        int start=data.length-2-length, end=start-16;
        while(b.get()!=0) {}
        var ops=new ArrayList<Instruction>();
        while(b.position()<end) {
            int op=b.getShort()&65535; Object arg;
            if(op==3) { var s=new StringBuilder(); for(int c;(c=b.get()&255)!=0;)s.append((char)c); arg=s.toString(); }
            else if(op>=100 || op==21 || op==38 || op==39) arg=b.get()&255;
            else arg=b.getInt();
            ops.add(new Instruction(op,arg));
        }
        b.position(start); int count=b.get()&255; var tables=new ArrayList<Map<Integer,Integer>>();
        for(int n=0;n<count;n++) { int size=b.getShort()&65535; var table=new TreeMap<Integer,Integer>(); for(int j=0;j<size;j++)table.put(b.getInt(),b.getInt()); tables.add(table); }
        if(b.position()!=data.length-2) throw new IllegalStateException("Script footer");
        return new Script(ops,tables);
    }
    static boolean has(Script s,int op,int arg) { return s.instructions.contains(new Instruction(op,arg)); }
    public static void main(String[] args) throws Exception {
        Path root=Path.of("").toAbsolutePath(); Path cache=root.resolve(".data/cache/LIVE");
        try(var index=new RandomAccessFile(cache.resolve("main_file_cache.idx12").toFile(),"r"); var data=new RandomAccessFile(cache.resolve("main_file_cache.dat2").toFile(),"r")) {
            for(int id=0;id<index.length()/6;id++) {
                byte[] bytes=group(index,data,id); if(bytes==null)continue;
                Script s; try { s=decode(bytes); } catch(RuntimeException ex) {continue;}
                boolean match=id==5927 || id==1934 || id==5940 || id==5941 || id==5942 || has(s,40,5927);
                for(int varc:new int[]{516,521,522,982,1147}) match|=has(s,42,varc)||has(s,43,varc);
                if(!match)continue;
                System.out.println("SCRIPT "+id+" instructions="+s.instructions+" switches="+s.switches);
                if(id==5927) {
                    int switchIndex=s.instructions.indexOf(new Instruction(60,0));
                    for(var entry:s.switches.getFirst().entrySet()) {
                        int target=switchIndex+1+entry.getValue(); var branch=new ArrayList<Instruction>();
                        while(target<s.instructions.size()) {var op=s.instructions.get(target++); branch.add(op); if(op.opcode==6 || op.opcode==21)break;}
                        boolean relevant=branch.stream().anyMatch(op->op.opcode==43 && op.argument instanceof Integer i && (i>=515&&i<=524 || i==982 || i==1147));
                        if(relevant)System.out.println("ARCEUUS_BUFF_CASE key="+entry.getKey()+" branch="+branch);
                    }
                }
                if(id==5923) {
                    int switchIndex=s.instructions.indexOf(new Instruction(60,0));
                    for(int key:new int[]{3120,3125,3126,3128}) {
                        int target=switchIndex+1+s.switches.getFirst().get(key);
                        System.out.println("ARCEUUS_BUFF_READER key="+key+" instructions="+s.instructions.subList(target,Math.min(target+35,s.instructions.size())));
                    }
                }
            }
        }
    }
}
