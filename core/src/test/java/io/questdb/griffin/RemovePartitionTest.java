/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin;

import io.questdb.cairo.TableReader;
import io.questdb.cairo.TableWriter;
import io.questdb.cairo.sql.Record;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.griffin.engine.functions.rnd.SharedRandom;
import io.questdb.std.Rnd;
import io.questdb.std.datetime.microtime.Timestamps;
import io.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RemovePartitionTest extends AbstractGriffinTest {

    @Before
    public void setUp3() {
        SharedRandom.RANDOM.set(new Rnd());
    }

    @Test
    public void testRemoveSeveralFromTop() throws Exception {
        assertMemoryLeak(() -> {
            compiler.compile("create table tst as (select * from (select rnd_int() a, rnd_double() b, timestamp_sequence(0, 1000000000l) t from long_sequence(1000)) timestamp (t)) timestamp(t) partition by DAY", sqlExecutionContext);

            try (
                    TableReader reader = engine.getReader(sqlExecutionContext.getCairoSecurityContext(), "tst");
                    TableWriter writer = engine.getWriter(sqlExecutionContext.getCairoSecurityContext(), "tst")
            ) {
                // utilise reader fully
                RecordCursor cursor = reader.getCursor();
                final Record record = cursor.getRecord();

                double superSum = 0;
                while (cursor.hasNext()) {
                    superSum = record.getInt(0) + record.getDouble(1) + record.getTimestamp(1);
                }

                Assert.assertEquals(4.6023174787925821E18, superSum, 0.00001);

                reader.reload();

                long timestampToDelete = 0;
                for (int i = 0; i < 10; i++) {
                    writer.removePartition(timestampToDelete + (i * Timestamps.DAY_MICROS));
                }
                writer.commit();

                reader.reload();
                cursor.toTop();

                String expectedAfterPartitionDelete = "a\tb\tt\n" +
                        "1576838676\t0.08057877795249069\t1970-01-11T00:00:00.000000Z\n" +
                        "-684086147\t0.3806487278648183\t1970-01-11T00:16:40.000000Z\n" +
                        "-689922587\t0.2698247661309793\t1970-01-11T00:33:20.000000Z\n" +
                        "-1918552401\t0.4531424919285716\t1970-01-11T00:50:00.000000Z\n" +
                        "-1004453257\t0.6085797228669808\t1970-01-11T01:06:40.000000Z\n" +
                        "-765083634\t0.4524050162454656\t1970-01-11T01:23:20.000000Z\n" +
                        "316094146\t0.12003762630199555\t1970-01-11T01:40:00.000000Z\n" +
                        "1068342573\t0.7082464127490494\t1970-01-11T01:56:40.000000Z\n" +
                        "-864537248\t0.1040698566401328\t1970-01-11T02:13:20.000000Z\n" +
                        "1543752250\t0.18600146920290805\t1970-01-11T02:30:00.000000Z\n" +
                        "-890802851\t0.22928529042164025\t1970-01-11T02:46:40.000000Z\n" +
                        "1672937587\t0.8520295549109822\t1970-01-11T03:03:20.000000Z\n" +
                        "-98437117\t0.26682634958461626\t1970-01-11T03:20:00.000000Z\n" +
                        "1278648509\t0.4121046775544003\t1970-01-11T03:36:40.000000Z\n" +
                        "868935708\t0.05009031512448925\t1970-01-11T03:53:20.000000Z\n" +
                        "1553180848\t0.7109170062462249\t1970-01-11T04:10:00.000000Z\n" +
                        "1608930002\t0.9886838651440645\t1970-01-11T04:26:40.000000Z\n" +
                        "848077524\t0.29684625166336664\t1970-01-11T04:43:20.000000Z\n" +
                        "-872317779\t0.9455731567461921\t1970-01-11T05:00:00.000000Z\n" +
                        "-2142917800\t0.16525810396139984\t1970-01-11T05:16:40.000000Z\n" +
                        "1060889076\t0.5173940547749024\t1970-01-11T05:33:20.000000Z\n" +
                        "1185624407\t0.7544676970803467\t1970-01-11T05:50:00.000000Z\n" +
                        "-1677723040\t0.13986493405162936\t1970-01-11T06:06:40.000000Z\n" +
                        "852766828\t0.49324528227010966\t1970-01-11T06:23:20.000000Z\n" +
                        "841483165\t0.11070938825827936\t1970-01-11T06:40:00.000000Z\n" +
                        "-1155434911\t0.00978072311811784\t1970-01-11T06:56:40.000000Z\n" +
                        "1362727008\t0.44599963282948984\t1970-01-11T07:13:20.000000Z\n" +
                        "1192991824\t0.3659276421170724\t1970-01-11T07:30:00.000000Z\n" +
                        "1556431414\t0.026301401183052797\t1970-01-11T07:46:40.000000Z\n" +
                        "32338029\t0.08328091072953259\t1970-01-11T08:03:20.000000Z\n" +
                        "-1097218488\t0.06790969300705241\t1970-01-11T08:20:00.000000Z\n" +
                        "-499968873\t0.2615339514777504\t1970-01-11T08:36:40.000000Z\n" +
                        "-1874709376\t0.45771492702062366\t1970-01-11T08:53:20.000000Z\n" +
                        "319944829\t0.20406886183355866\t1970-01-11T09:10:00.000000Z\n" +
                        "772080999\t0.27575788473179963\t1970-01-11T09:26:40.000000Z\n" +
                        "16459753\t0.6409584133875881\t1970-01-11T09:43:20.000000Z\n" +
                        "1290921378\t0.2713721519500688\t1970-01-11T10:00:00.000000Z\n" +
                        "375593536\t0.04336526294979903\t1970-01-11T10:16:40.000000Z\n" +
                        "1952855598\t0.4083056333675016\t1970-01-11T10:33:20.000000Z\n" +
                        "561962643\t0.7736395764837526\t1970-01-11T10:50:00.000000Z\n" +
                        "-1776012188\t0.17405527561965717\t1970-01-11T11:06:40.000000Z\n" +
                        "1399730601\t0.3854235867370095\t1970-01-11T11:23:20.000000Z\n" +
                        "1499465486\t0.37563375709147684\t1970-01-11T11:40:00.000000Z\n" +
                        "850982263\t0.41258159186565424\t1970-01-11T11:56:40.000000Z\n" +
                        "-1211498921\t0.4217546132070882\t1970-01-11T12:13:20.000000Z\n" +
                        "-518985515\t0.23889505021865998\t1970-01-11T12:30:00.000000Z\n" +
                        "1478600023\t0.5340640161021565\t1970-01-11T12:46:40.000000Z\n" +
                        "-757643200\t0.7700469744126617\t1970-01-11T13:03:20.000000Z\n" +
                        "91266345\t0.6601629127014469\t1970-01-11T13:20:00.000000Z\n" +
                        "956610416\t0.9778843606510285\t1970-01-11T13:36:40.000000Z\n" +
                        "-1444192781\t0.06585925388777292\t1970-01-11T13:53:20.000000Z\n" +
                        "701841024\t0.6670228450708864\t1970-01-11T14:10:00.000000Z\n" +
                        "1863548118\t0.4653998788482615\t1970-01-11T14:26:40.000000Z\n" +
                        "1444864868\t0.41265788151170835\t1970-01-11T14:43:20.000000Z\n" +
                        "1797835122\t0.1914161844876806\t1970-01-11T15:00:00.000000Z\n" +
                        "-1812360042\t0.5469257570499296\t1970-01-11T15:16:40.000000Z\n" +
                        "-1982095870\t0.20869523440218085\t1970-01-11T15:33:20.000000Z\n" +
                        "-1040917954\t0.47046214502342254\t1970-01-11T15:50:00.000000Z\n" +
                        "1558850992\t0.5277233818830627\t1970-01-11T16:06:40.000000Z\n" +
                        "-519239966\t0.2263345107996424\t1970-01-11T16:23:20.000000Z\n" +
                        "1700554356\t0.8375723114173276\t1970-01-11T16:40:00.000000Z\n" +
                        "-1734368808\t0.2973413048547613\t1970-01-11T16:56:40.000000Z\n" +
                        "-251197254\t0.02206807161850266\t1970-01-11T17:13:20.000000Z\n" +
                        "1967503676\t0.7708094983496303\t1970-01-11T17:30:00.000000Z\n" +
                        "171793346\t0.7774992993301526\t1970-01-11T17:46:40.000000Z\n" +
                        "1685464327\t0.8711554561766037\t1970-01-11T18:03:20.000000Z\n" +
                        "-702939849\t0.2353405108400326\t1970-01-11T18:20:00.000000Z\n" +
                        "118335726\t0.17485263263815565\t1970-01-11T18:36:40.000000Z\n" +
                        "-1641406479\t0.19882280937094632\t1970-01-11T18:53:20.000000Z\n" +
                        "1637478680\t0.8451212749109064\t1970-01-11T19:10:00.000000Z\n" +
                        "603472290\t0.362912927226985\t1970-01-11T19:26:40.000000Z\n" +
                        "-1987835306\t0.35083644032896444\t1970-01-11T19:43:20.000000Z\n" +
                        "1845426751\t0.4179029808821145\t1970-01-11T20:00:00.000000Z\n" +
                        "-983118321\t0.05905539454236053\t1970-01-11T20:16:40.000000Z\n" +
                        "-931312258\t0.19751729781600535\t1970-01-11T20:33:20.000000Z\n" +
                        "2096767443\t0.35820401665170765\t1970-01-11T20:50:00.000000Z\n" +
                        "-186002027\t0.06499118875100096\t1970-01-11T21:06:40.000000Z\n" +
                        "210529946\t0.8426876468436052\t1970-01-11T21:23:20.000000Z\n" +
                        "316613958\t0.7947985205275171\t1970-01-11T21:40:00.000000Z\n" +
                        "2007373598\t0.2443292452654071\t1970-01-11T21:56:40.000000Z\n" +
                        "-1973328009\t0.09990632318082981\t1970-01-11T22:13:20.000000Z\n" +
                        "-2050693451\t0.8935939683581362\t1970-01-11T22:30:00.000000Z\n" +
                        "-677022047\t0.10811223855547059\t1970-01-11T22:46:40.000000Z\n" +
                        "1194557424\t0.3236005778073523\t1970-01-11T23:03:20.000000Z\n" +
                        "856082190\t0.09028864342090992\t1970-01-11T23:20:00.000000Z\n" +
                        "-1037890340\t0.8731939170484669\t1970-01-11T23:36:40.000000Z\n" +
                        "-273166864\t0.3883845787817346\t1970-01-11T23:53:20.000000Z\n" +
                        "519573739\t0.6914252886783999\t1970-01-12T00:10:00.000000Z\n" +
                        "-1059163764\t0.5807816609951174\t1970-01-12T00:26:40.000000Z\n" +
                        "-1248364069\t0.47180077197626313\t1970-01-12T00:43:20.000000Z\n" +
                        "-707410819\t0.17675145949691562\t1970-01-12T01:00:00.000000Z\n" +
                        "694073645\t0.17231107928084932\t1970-01-12T01:16:40.000000Z\n" +
                        "1143841840\t0.11572097670276393\t1970-01-12T01:33:20.000000Z\n" +
                        "-1203620259\t0.10992274280613101\t1970-01-12T01:50:00.000000Z\n" +
                        "1001513017\t0.494968979129106\t1970-01-12T02:06:40.000000Z\n" +
                        "-1397486016\t0.13260889791591712\t1970-01-12T02:23:20.000000Z\n" +
                        "-124098231\t0.5418085488978492\t1970-01-12T02:40:00.000000Z\n" +
                        "1375230944\t0.36658512235236584\t1970-01-12T02:56:40.000000Z\n" +
                        "-1602836197\t0.9557940492957487\t1970-01-12T03:13:20.000000Z\n" +
                        "-903320803\t0.1570704400490346\t1970-01-12T03:30:00.000000Z\n" +
                        "260033364\t0.034904847183765186\t1970-01-12T03:46:40.000000Z\n" +
                        "-1913086326\t0.5749982450146361\t1970-01-12T04:03:20.000000Z\n" +
                        "373991096\t0.13360028796545642\t1970-01-12T04:20:00.000000Z\n" +
                        "2000510449\t0.5810641448245488\t1970-01-12T04:36:40.000000Z\n" +
                        "-2081389237\t0.3443944218017603\t1970-01-12T04:53:20.000000Z\n" +
                        "-959535924\t0.44725548780773694\t1970-01-12T05:10:00.000000Z\n" +
                        "-1251112810\t0.6933103859471981\t1970-01-12T05:26:40.000000Z\n" +
                        "1732259734\t0.41876634576982885\t1970-01-12T05:43:20.000000Z\n" +
                        "-987366968\t0.7437656766929067\t1970-01-12T06:00:00.000000Z\n" +
                        "556907040\t0.32010882429399834\t1970-01-12T06:16:40.000000Z\n" +
                        "1569770272\t0.4288965848487438\t1970-01-12T06:33:20.000000Z\n" +
                        "1405080657\t0.18027951967123457\t1970-01-12T06:50:00.000000Z\n" +
                        "526853349\t0.6178183982086626\t1970-01-12T07:06:40.000000Z\n" +
                        "-334179339\t0.8261516071359104\t1970-01-12T07:23:20.000000Z\n" +
                        "-17513564\t0.36138284225880035\t1970-01-12T07:40:00.000000Z\n" +
                        "354098377\t0.6953541428262865\t1970-01-12T07:56:40.000000Z\n" +
                        "1443031361\t0.31908901955539515\t1970-01-12T08:13:20.000000Z\n" +
                        "1175675185\t0.05899019706036246\t1970-01-12T08:30:00.000000Z\n" +
                        "99560093\t0.16347836851309816\t1970-01-12T08:46:40.000000Z\n" +
                        "-1674594591\t0.1407735833402206\t1970-01-12T09:03:20.000000Z\n" +
                        "-1124656535\t0.1528299057907735\t1970-01-12T09:20:00.000000Z\n" +
                        "-1507142148\t0.00825969166045648\t1970-01-12T09:36:40.000000Z\n" +
                        "2049048212\t0.29309813796291284\t1970-01-12T09:53:20.000000Z\n" +
                        "352894170\t0.5832447623746595\t1970-01-12T10:10:00.000000Z\n" +
                        "1513826357\t0.5063850654335724\t1970-01-12T10:26:40.000000Z\n" +
                        "1638798442\t0.28053790406505974\t1970-01-12T10:43:20.000000Z\n" +
                        "-501102817\t0.5524335828965579\t1970-01-12T11:00:00.000000Z\n" +
                        "-2110403420\t0.8114444771798898\t1970-01-12T11:16:40.000000Z\n" +
                        "-1041070703\t0.6810629367436306\t1970-01-12T11:33:20.000000Z\n" +
                        "1774933838\t0.8545511357260956\t1970-01-12T11:50:00.000000Z\n" +
                        "-1189092035\t0.5009939966556752\t1970-01-12T12:06:40.000000Z\n" +
                        "1108319297\t0.4007457065265131\t1970-01-12T12:23:20.000000Z\n" +
                        "1674123731\t0.2679292897042268\t1970-01-12T12:40:00.000000Z\n" +
                        "782820973\t0.9391935526421991\t1970-01-12T12:56:40.000000Z\n" +
                        "413500238\t0.19676893721907063\t1970-01-12T13:13:20.000000Z\n" +
                        "-227038447\t0.4799415921194079\t1970-01-12T13:30:00.000000Z\n";

                TestUtils.assertReader(
                        expectedAfterPartitionDelete,
                        reader,
                        sink
                );
            }
        });
    }
}
