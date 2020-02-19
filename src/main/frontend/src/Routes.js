import React from "react";
import { Route, Switch, Redirect } from "react-router-dom";
import Home from "./containers/Home";
import Login from "./containers/Login";

import AppliedRoute from "./routers/AppliedRoute";
import AuthenticatedRoute from "./routers/AuthenticatedRoute";
import UnauthenticatedRoute from "./routers/UnauthenticatedRoute";


export default function Routes({ appProps }) {
  return (
    <Switch>
      <AppliedRoute path="/" exact component={Home} appProps={appProps} />
      <UnauthenticatedRoute path="/login" exact component={Login} appProps={appProps} />
      <Route exact path="*"  appProps={appProps} render={() => (
        <Redirect appProps={appProps} to="/" />
      )} />
    </Switch>
  );
}