/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel
 * 
 * This file is part of Aphelion
 * 
 * Aphelion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * Aphelion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with Aphelion.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * In addition, the following supplemental terms apply, based on section 7 of
 * the GNU Affero General Public License (version 3):
 * a) Preservation of all legal notices and author attributions
 * b) Prohibition of misrepresentation of the origin of this material, and
 * modified versions are required to be marked in reasonable ways as
 * different from the original version (for example by appending a copyright notice).
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU Affero General Public License cover the whole combination.
 * 
 * As a special exception, the copyright holders of this library give you 
 * permission to link this library with independent modules to produce an 
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your 
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module. An independent
 * module is a module which is not derived from or based on this library.
 */
package aphelion.client.net;

import aphelion.shared.event.ClockSource;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** This object provides clock synchronization with a server.
 * The client should send multiple time requests during the connection 
 * phase and periodically during gameplay (but never when downloading).
 * 
 * A time request consists of the local timestamp (such as System.nanoTime()).
 * The server must reply with a response as soon as possible containing the
 * timestamp given by the client unmodified and its own timestamp.
 * 
 * These values are used to estimate latency and calculate a clock difference.
 * (The technique that is used to do this is listed below.)
 * 
 * The technique that is used is optimized for TCP unlike protocols like 
 * NTP which require UDP. It does this by collecting many samples and discarding 
 * samples that are most likely the result of a retransmission.
 * 
 * The time sync is repeated periodically during gameplay in combination with 
 * removing very old entries to mitigate any clock drift between the server and 
 * clients.
 * 
 * Zachary Booth Simpson. A Stream-based Time Synchronization Technique For 
 * Networked Computer Games, 2000. 
 * http://www.mine-control.com/zack/timesync/timesync.html
 * 
 * @author Joris
 */

public class ClockSync implements ClockSource
{
        private final int entryLimit;
        private final List<Entry> entries = new ArrayList<>(); // sorted
        private volatile boolean hasResponse = false;
        private volatile long offset;
        
        public ClockSync(int entryLimit)
        {
                this.entryLimit = entryLimit;
        }
        
        /** Register a response from the server.
         *
         * @param receivedAt The nano time at which the response was received
         * @param clientRequestTime When was the request sent?
         * @param serverNanoTime What (nano) time value did the server send us
         */
        @ThreadSafe
        public synchronized void addResponse(long receivedAt, long clientRequestTime, long serverNanoTime)
        {
                long latency = (receivedAt - clientRequestTime) / 2;
                long timeOffset = serverNanoTime - receivedAt + latency;
                Entry entry = new Entry(timeOffset, System.nanoTime());

                int index = Collections.binarySearch(entries, entry);
                if (index < 0)
                {
                        index = -(index + 1);
                }
                entries.add(index, entry);
                
                while(entries.size() >= entryLimit)
                {
                        removeOldest();
                }
                
                offset = calculateOffset(); // ofset is only set so that the getters do not need to lock
                hasResponse = true; // set me AFTER setting offset to gaurantee thread safety
        }
        
        private void removeOldest()
        {
                long now = System.nanoTime();
                
                if (entries.isEmpty())
                {
                        return;
                }
                
                // compare ages (instead of using the smallest value) so that nanoTime wraparound is handled properly
                
                int oldestIndex = 0;
                long oldestAge = now - entries.get(oldestIndex).addedAt;
                
                for (int a = 1; a < entries.size(); ++a)
                {
                        Entry timeOffset = entries.get(a);
                        
                        long age = now - timeOffset.addedAt;
                        
                        if (age > oldestAge)
                        {
                                oldestAge = age;
                                oldestIndex = a;
                        }
                }
                
                entries.remove(oldestIndex);
                
        }
        
        private long calculateOffset()
        {
                double mean = calculateMean();
                double deviation = calculateStandardDeviation(mean);
                double median = calculateMedian();
                
                double total = 0;
                long values = 0;
                
                for (Entry timeOffset : entries)
                {
                        if (Math.abs(timeOffset.timeOffset - median) <= deviation)
                        {
                                total += timeOffset.timeOffset;
                                ++values;
                        }
                }
                
                if (values == 0)
                {
                        return 0;
                }
                
                return (long) (total / values);
        }
        
        private double calculateMean()
        {
                double total = 0;
                long values = 0;
                
                for (Entry timeOffset : entries)
                {
                        total += timeOffset.timeOffset;
                        ++values;
                }
                
                if (values == 0)
                {
                        return 0;
                }
                
                return total / values;
        }
        
        private double calculateStandardDeviation(double mean)
        {
                double total = 0;
                long values = 0;
                
                for (Entry timeOffset : entries)
                {
                        total += Math.pow(timeOffset.timeOffset - mean, 2);
                        ++values;
                }
                
                if (values == 0)
                {
                        return 0;
                }
                
                return Math.sqrt(total / values);
        }
        
        private double calculateMedian()
        {
                int halfSize = entries.size() / 2;
                
                if (entries.size() % 2 == 0)
                {
                        double total = entries.get(halfSize - 1).timeOffset;
                        total += entries.get(halfSize).timeOffset;
                        return total / 2;
                }
                else
                {
                        return entries.get(halfSize).timeOffset;
                }
        }
        
        @ThreadSafe
        public boolean hasResponse()
        {
                return hasResponse;
        }
        
        @ThreadSafe
        public long getOffset()
        {
                if (!hasResponse())
                {
                        throw new IllegalStateException("Atleast one response must have been collected");
                }
                return offset;
        }

        @Override
        @ThreadSafe
        public long nanoTime()
        {
                return System.nanoTime() + getOffset();
        }
        
        private static class Entry implements Comparable<Entry>
        {
                final long timeOffset;
                final long addedAt;

                Entry(long timeOffset, long addedAt)
                {
                        this.timeOffset = timeOffset;
                        this.addedAt = addedAt;
                }

                @Override
                public int compareTo(Entry o)
                {
                        return Long.compare(timeOffset, o.timeOffset);
                }

                @Override
                public boolean equals(Object obj)
                {
                        if (obj instanceof Entry)
                        {
                                return timeOffset == ((Entry)obj).timeOffset;
                        }
                        return false;
                }

                @Override
                public int hashCode()
                {
                        int hash = 7;
                        hash = 31 * hash + (int) (this.timeOffset ^ (this.timeOffset >>> 32));
                        return hash;
                }
        }
}
