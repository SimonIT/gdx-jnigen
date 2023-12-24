package com.badlogic.gdx.jnigen.gradle;

import org.gradle.api.tasks.Delete;

import javax.inject.Inject;

public class JnigenCleanTask extends Delete {
	JnigenExtension ext;

	@Inject
	public JnigenCleanTask(JnigenExtension ext) {
		this.ext = ext;

		setGroup("jnigen");
		setDescription("Deletes all generated files.");
		delete(ext.subProjectDir + ext.temporaryDir, ext.subProjectDir + ext.libsDir);
	}
}
