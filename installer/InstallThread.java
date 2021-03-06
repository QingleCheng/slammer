/*
 * InstallThread.java
 *
 * Originally written by Slava Pestov for the jEdit installer project. This work
 * has been placed into the public domain. You may use this work in any way and
 * for any purpose you wish.
 *
 * THIS SOFTWARE IS PROVIDED AS-IS WITHOUT WARRANTY OF ANY KIND, NOT EVEN THE
 * IMPLIED WARRANTY OF MERCHANTABILITY. THE AUTHOR OF THIS SOFTWARE, ASSUMES
 * _NO_ RESPONSIBILITY FOR ANY CONSEQUENCE RESULTING FROM THE USE, MODIFICATION,
 * OR REDISTRIBUTION OF THIS SOFTWARE.
 */
package installer;

import java.io.*;
import java.util.Vector;
import java.sql.*;

/*
 * The thread that performs installation.
 */
public class InstallThread extends Thread
{
	public InstallThread(Install installer, Progress progress,
		String installDir, OperatingSystem.OSTask[] osTasks,
		int size, Vector components, Vector indicies)
	{
		super("Install thread");

		this.installer = installer;
		this.progress = progress;
		this.installDir = installDir;
		this.osTasks = osTasks;
		this.size = size;
		this.components = components;
		this.indicies = indicies;
	}

	public void run()
	{
		// The *2 is so the last 50% can be used for db sync.
		progress.setMaximum(size * 1024 * 2);

		try
		{
			// install user-selected packages
			for(int i = 0; i < components.size(); i++)
			{
				String comp = (String)components.elementAt(i);
				System.err.println("Installing " + comp);
				installComponent(comp);
			}

			// do operating system specific stuff (creating startup
			// scripts, installing man pages, etc.)
			for(int i = 0; i < osTasks.length; i++)
			{
				System.err.println("Performing task " +
					osTasks[i].getName());
				osTasks[i].perform(installDir,components);
			}
		}
		catch(FileNotFoundException fnf)
		{
			progress.error("The installer could not create the "
				+ "destination directory.\n"
				+ "Maybe you do not have write permission?");
			return;
		}
		catch(IOException io)
		{
			progress.error(io.toString());
			return;
		}

		// File install is done, begin database sync

		try
		{
			int sqllen = 0;

			// start the database
			String DBloc = installDir + File.separator + "programs" + File.separator + "database" + File.separator + "db";
			File f = new File(DBloc);
			boolean newDB = !f.exists();

			startdb(newDB);

			// install any .sql files
			String sqlfile;

			for(int j = 0; j < components.size(); j++)
			{
				sqlfile = installer.getProperty("comp." + ((Integer)indicies.elementAt(j)).toString() + ".sql");

				if(sqlfile != null)
				{
					String outfile = installDir + File.separator
						+ "records" + File.separator + sqlfile;

					InputStream in = new BufferedInputStream(
					getClass().getResourceAsStream("/records/" + sqlfile));

					if(in == null)
						throw new FileNotFoundException(sqlfile);

					installer.copy(in, outfile, null);
					in.close();

					dbvect.addElement(sqlfile);

					sqllen += installer.getIntegerProperty("comp." + ((Integer)indicies.elementAt(j)).toString() + ".sqllen");
				}
			}

			progress.setMaximum(sqllen * 2);
			progress.advance(sqllen); // go back to 50%

			String q = "insert into data (eq, record, digi_int, mom_mag, arias, dobry, pga, pgv, mean_per, epi_dist, foc_dist, rup_dist, vs30, class, foc_mech, location, owner, latitude, longitude, change, path, select1, analyze, select2) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			for(int i = 0; i < dbvect.size(); i++)
			{
				sqlfile = (String)dbvect.elementAt(i);

				FileReader fr = new FileReader(installDir + File.separator
					+ "records" + File.separator + sqlfile);
				String s = "";
				String cur[] = new String[DB_LENGTH];
				int j = 0;
				char c;
				String path;

				while(fr.ready())
				{
					c = (char)fr.read();

					switch(c)
					{
						case '\r':
							break;
						case '\t':
							cur[j] = s;
							j++;
							s = "";
							break;
						case '\n':
						{
							cur[j] = s;
							s = "";
							j = 0;

							if(countQuery("select count(*) from data where eq=? and record=?", cur[0], cur[1]) > 0)
							{
								System.out.println(cur[0] + " - " + cur[1] + ": already in db");
							}
							else
							{
								progress.advance(1);
								path = installDir + File.separator + "records" + File.separator + cur[0] + File.separator + cur[1];
								PreparedStatement ps = preparedStatement(q);
								int idx = 1;
								ps.setObject(idx++, cur[DB_eq]);
								ps.setObject(idx++, cur[DB_record]);
								ps.setObject(idx++, cur[DB_digi_int]);
								ps.setObject(idx++, nullify(cur[DB_mom_mag]), Types.VARCHAR);
								ps.setObject(idx++, cur[DB_arias]);
								ps.setObject(idx++, cur[DB_dobry]);
								ps.setObject(idx++, cur[DB_pga]);
								ps.setObject(idx++, cur[DB_pgv]);
								ps.setObject(idx++, cur[DB_mean_per]);
								ps.setObject(idx++, nullify(cur[DB_epi_dist]), Types.VARCHAR);
								ps.setObject(idx++, nullify(cur[DB_foc_dist]), Types.VARCHAR);
								ps.setObject(idx++, nullify(cur[DB_rup_dist]), Types.VARCHAR);
								ps.setObject(idx++, nullify(cur[DB_vs30]), Types.VARCHAR);
								ps.setObject(idx++, cur[DB_class]);
								ps.setObject(idx++, cur[DB_foc_mech]);
								ps.setObject(idx++, cur[DB_location]);
								ps.setObject(idx++, cur[DB_owner]);
								ps.setObject(idx++, nullify(cur[DB_latitude]), Types.VARCHAR);
								ps.setObject(idx++, nullify(cur[DB_longitude]), Types.VARCHAR);
								ps.setObject(idx++, 0);
								ps.setObject(idx++, path);
								ps.setObject(idx++, 0);
								ps.setObject(idx++, 0);
								ps.setObject(idx++, 0);
								ps.executeUpdate();
								ps.close();
							}

							break;
						}
						default:
							s += c;
							break;
					}
				}
			}


			preparedUpdate("update data set " +
				"mag_srch=cast(cast(mom_mag as decimal(10,4)) as double), " +
				"epi_srch=cast(cast(epi_dist as decimal(10,4)) as double), " +
				"foc_srch=cast(cast(foc_dist as decimal(10,4)) as double), " +
				"rup_srch=cast(cast(rup_dist as decimal(10,4)) as double), " +
				"vs30_srch=cast(cast(vs30 as decimal(10,4)) as double), " +
				"lat_srch=cast(cast(latitude as decimal(20,15)) as double), " +
				"lng_srch=cast(cast(longitude as decimal(20,15)) as double)"
			);

			closedb();
		}
		catch(Exception ex)
		{
			progress.error(ex.toString());
			ex.printStackTrace();
		}

		// done with db sync

		progress.done();
	}

	// private members
	private Install installer;
	private Progress progress;
	private String installDir;
	private OperatingSystem.OSTask[] osTasks;
	private int size;
	private Vector components;
	private Vector indicies;

	private void installComponent(String name) throws IOException
	{
		InputStream in = new BufferedInputStream(
			getClass().getResourceAsStream(name + ".tar.bz2"));
		// skip header bytes
		// maybe should check if they're valid or not?
		in.read();
		in.read();

		TarInputStream tarInput = new TarInputStream(
			new CBZip2InputStream(in));
		TarEntry entry;
		while((entry = tarInput.getNextEntry()) != null)
		{
			if(entry.isDirectory())
				continue;
			String fileName = entry.getName();
			//System.err.println(fileName);
			String outfile = installDir + File.separator
				+ fileName.replace('/',File.separatorChar);
			installer.copy(tarInput,outfile,progress);
		}

		tarInput.close();
	}

	// custom database stuff

	// order of fields for the eq.sql file
	public static final int DB_eq        = 0;
	public static final int DB_record    = 1;
	public static final int DB_digi_int  = 2;
	public static final int DB_mom_mag   = 3;
	public static final int DB_arias     = 4;
	public static final int DB_dobry     = 5;
	public static final int DB_pga       = 6;
	public static final int DB_pgv       = 7;
	public static final int DB_mean_per  = 8;
	public static final int DB_epi_dist  = 9;
	public static final int DB_foc_dist  = 10;
	public static final int DB_rup_dist  = 11;
	public static final int DB_vs30      = 12;
	public static final int DB_class     = 13;
	public static final int DB_foc_mech  = 14;
	public static final int DB_location  = 15;
	public static final int DB_owner     = 16;
	public static final int DB_latitude  = 17;
	public static final int DB_longitude = 18;
	public static final int DB_LENGTH    = 19;

	private java.sql.Connection connection = null;
	public static final String url = "jdbc:derby:db";
	private Vector dbvect = new Vector();

	public void startdb(boolean newDB) throws Exception
	{
		if(connection != null)
			return;

		System.setProperty("derby.system.home", installDir + File.separator + "programs" + File.separator + "database");
		Class.forName("org.apache.derby.jdbc.EmbeddedDriver");

		String connect_url = url;
		if(newDB)
			connect_url += ";create=true";

		connection = java.sql.DriverManager.getConnection(connect_url);

		if(newDB)
		{
			preparedUpdate("create table data ("
				+ "id        integer      not null generated always as identity primary key,"
				+ "eq        varchar(100) not null,"
				+ "record    varchar(100) not null,"
				+ "digi_int  double       not null,"
				+ "mom_mag   varchar(20)          ,"
				+ "arias     double       not null,"
				+ "dobry     double       not null,"
				+ "pga       double       not null,"
				+ "pgv       double       not null,"
				+ "mean_per  double       not null,"
				+ "epi_dist  varchar(20)          ,"
				+ "foc_dist  varchar(20)          ,"
				+ "rup_dist  varchar(20)          ,"
				+ "vs30      varchar(20)          ,"
				+ "class     smallint     not null,"
				+ "foc_mech  smallint     not null,"
				+ "location  varchar(100) not null,"
				+ "owner     varchar(100) not null,"
				+ "latitude  varchar(20)          ,"
				+ "longitude varchar(20)          ,"
				+ "change    smallint     not null,"
				+ "path      varchar(255) not null,"
				+ "select1   smallint     not null,"
				+ "analyze   smallint     not null,"
				+ "select2   smallint     not null,"
				+ "mag_srch  double,"
				+ "epi_srch  double,"
				+ "foc_srch  double,"
				+ "rup_srch  double,"
				+ "vs30_srch double,"
				+ "lat_srch  double,"
				+ "lng_srch  double"
			+ ")");

			preparedUpdate("create table grp ("
				+ "record    integer      not null,"
				+ "name      varchar(100) not null,"
				+ "analyze   smallint     not null"
				+ ")");
		}
	}

	public int preparedUpdate(String update, Object... objects) throws SQLException
	{
		PreparedStatement ps = connection.prepareStatement(update);
		for(int i = 0; i < objects.length; i++)
		{
			ps.setObject(i+1, objects[i]);
		}
		int i = ps.executeUpdate();
		ps.close();
		return i;
	}

	public PreparedStatement preparedStatement(String s) throws SQLException
	{
		return connection.prepareStatement(s);
	}

	private int countQuery(String query, Object... objects) throws SQLException
	{
		PreparedStatement ps = connection.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		for(int i = 0; i < objects.length; i++)
		{
			ps.setObject(i+1, objects[i]);
		}
		ResultSet result = ps.executeQuery();
		result.next();
		int ret = result.getInt(1);
		ps.close();
		return ret;
	}

	public void closedb() throws SQLException
	{
		if(connection != null)
		{
			connection.close();
			connection = null;
			try {
				java.sql.DriverManager.getConnection(url + ";shutdown=true");
			} catch(Exception e) {}
		}
	}

	public String nullify(String s) {
		if (s.equals("")) {
			return null;
		}
		return s;
	}
}
